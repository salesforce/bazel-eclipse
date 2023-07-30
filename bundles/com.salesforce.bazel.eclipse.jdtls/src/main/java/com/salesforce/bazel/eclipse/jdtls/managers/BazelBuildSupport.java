package com.salesforce.bazel.eclipse.jdtls.managers;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_BUILD;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_BUILD_BAZEL;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_DOT_BAZELPROJECT;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.DigestStore;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;

@SuppressWarnings("restriction")
public class BazelBuildSupport implements IBuildSupport {

    private static final String UNSUPPORTED_OPERATION_MESSAGE =
            "Unsupported operation. Please use BUILD.bazel/BUILD file to manage the source directories of a Bazel target.";

    private static final List<String> WATCH_FILE_PATTERNS;
    static {
        Set<String> fileNames = new TreeSet<>(BazelWorkspace.WORKSPACE_BOUNDARY_FILES);
        fileNames.add(FILE_NAME_BUILD_BAZEL);
        fileNames.add(FILE_NAME_BUILD);
        fileNames.add(FILE_NAME_DOT_BAZELPROJECT);
        WATCH_FILE_PATTERNS = fileNames.stream().map(s -> "**/" + s).collect(toUnmodifiableList());
    }

    private final DigestStore digestStore;

    public BazelBuildSupport() {
        this.digestStore = JavaLanguageServerPlugin.getDigestStore();
    }

    @Override
    public boolean applies(IProject project) {
        return ProjectUtils.hasNature(project, BAZEL_NATURE_ID);
    }

    @Override
    public String buildToolName() {
        return "Bazel";
    }

    @Override
    public void discoverSource(IClassFile classFile, IProgressMonitor monitor) throws CoreException {
        JavaLanguageServerPlugin.getDefaultSourceDownloader().discoverSource(classFile, monitor);
    }

    @Override
    public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor)
            throws CoreException {
        if ((resource == null) || !applies(resource.getProject())) {
            return false;
        }
        return IBuildSupport.super.fileChanged(resource, changeType, monitor) || isBuildFile(resource);
    }

    @Override
    public ILaunchConfiguration getLaunchConfiguration(IJavaProject javaProject, String scope) throws CoreException {
        return new JavaApplicationLaunchConfiguration(javaProject.getProject(), scope, null);
    }

    @Override
    public List<String> getWatchPatterns() {
        return WATCH_FILE_PATTERNS;
    }

    @Override
    public boolean isBuildFile(IResource resource) {
        return (resource != null) && (resource.getProject() != null) && (resource.getType() == IResource.FILE)
                && (BazelPackage.isBuildFileName(resource.getName())
                        || BazelWorkspace.isWorkspaceBoundaryFileName(resource.getName()));
    }

    @Override
    public boolean isBuildLikeFileName(String fileName) {
        return BazelPackage.isBuildFileName(fileName) || BazelWorkspace.isWorkspaceBoundaryFileName(fileName);
    }

    @Override
    public String unsupportedOperationMessage() {
        return UNSUPPORTED_OPERATION_MESSAGE;
    }

    @Override
    public void update(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
        if (!applies(project)) {
            return;
        }

        var bazelProject = BazelCore.create(project);
        var bazelWorkspace = bazelProject.getBazelWorkspace();

        var changed = false;
        if (bazelProject.isPackageProject()) {
            changed = digestStore.updateDigest(bazelProject.getBazelPackage().getBuildFile());
        } else if (bazelProject.isTargetProject()) {
            changed = digestStore.updateDigest(bazelProject.getBazelTarget().getBazelPackage().getBuildFile());
        } else {
            // check both WORKSPACE and .bazelproject
            changed = digestStore.updateDigest(bazelWorkspace.getWorkspaceFile())
                    | digestStore.updateDigest(bazelWorkspace.getBazelProjectViewFile());
        }

        if (changed || force) {
            JavaLanguageServerPlugin.logInfo("Starting Bazel update for workspace " + bazelWorkspace.getName());

            // TODO: add a better way to refresh individual projects
            var projectViewJob = new SynchronizeProjectViewJob(bazelWorkspace);

            // we don't schedule the job but execute it directly with the required rule
            project.getWorkspace()
                    .run(
                        projectViewJob::runInWorkspace,
                        projectViewJob.detectMissingRule(),
                        IWorkspace.AVOID_UPDATE,
                        monitor);
        }
    }
}
