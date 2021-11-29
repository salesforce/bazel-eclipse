package com.salesforce.b2eclipse.managers;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.ls.core.internal.ExtensionsExtractor;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.IProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ResourceUtils;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.component.EclipseBazelComponentFacade;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;

@SuppressWarnings("restriction")
public class BazelBuildSupport implements IBuildSupport {
    public static final String BUILD_FILE_NAME = "BUILD";
    public static final String WORKSPACE_FILE_NAME = "WORKSPACE";
    public static final String BAZELPROJECT_FILE_NAME_SUFIX = ".bazelproject";
    public static final String BAZEL_FILE_NAME_SUFIX = ".bazel";
    private static final List<String> WATCH_FILE_PATTERNS = Arrays.asList("**/" + BUILD_FILE_NAME,
        "**/" + WORKSPACE_FILE_NAME, "**/*" + BAZELPROJECT_FILE_NAME_SUFIX, "**/*" + BAZEL_FILE_NAME_SUFIX);
    private static final String BUILD_TOOL_NAME = "Bazel";
    private static final List<String> EXCLUDED_FILE_PATTERN = Arrays.asList("/bazel-*/**");

    private static List<String> calculatedExcludedFilePatterns = new ArrayList<>();

    private static final LogHelper LOG = LogHelper.log(MethodHandles.lookup().lookupClass());

    @Override
    public boolean applies(IProject project) {
        try {
            return project != null && project.hasNature(BazelNature.BAZEL_NATURE_ID);

        } catch (CoreException e) {
            return false;
        }
    }

    @Override
    public void update(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
        try {
            updateInternal(project, force, monitor);

        } catch (CoreException ex) {
            throw ex;

        } catch (AssertionFailedException ex) {
            // Bazel can't work with the provided project.
            // Just skip it.

        } catch (NoSuchElementException ex) {
            // No bazel importers found. This is definitely illegal situation
            // which is not clear how to solve right now.
            // Just skip it.
        }

    }

    protected void updateInternal(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {

        Assert.isTrue(applies(project));

        final IProjectImporter importer = obtainBazelImporter();

        importer.initialize(EclipseBazelComponentFacade.getInstance().getBazelWorkspaceRootDirectory());

        BazelCommandManager bazelCommandManager = EclipseBazelComponentFacade.getInstance().getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager
                .getWorkspaceCommandRunner(EclipseBazelComponentFacade.getInstance().getBazelWorkspace());

        bazelWorkspaceCmdRunner.flushAspectInfoCache();

        Assert.isTrue(importer.applies(monitor));

        importer.importToWorkspace(monitor);
    }

    @Override
    public boolean isBuildFile(IResource resource) {
        return resource != null && resource.getProject() != null && resource.getType() == IResource.FILE
                && (resource.getName().endsWith(BAZELPROJECT_FILE_NAME_SUFIX)
                        || resource.getName().endsWith(BAZEL_FILE_NAME_SUFIX)
                        || resource.getName().equals(BUILD_FILE_NAME)
                        || resource.getName().equals(WORKSPACE_FILE_NAME));
    }

    @Override
    public boolean isBuildLikeFileName(String fileName) {
        return fileName.endsWith(BAZELPROJECT_FILE_NAME_SUFIX) || fileName.equals(BUILD_FILE_NAME)
                || fileName.equals(WORKSPACE_FILE_NAME) || fileName.endsWith(BAZEL_FILE_NAME_SUFIX);
    }

    @Override
    public List<String> getWatchPatterns() {
        return WATCH_FILE_PATTERNS;
    }

    @Override
    public List<String> getExcludedFilePatterns() {
        return calculatedExcludedFilePatterns;
    }

    @Override
    public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor)
            throws CoreException {
        if (resource == null || !applies(resource.getProject())) {
            return false;
        }
        return IBuildSupport.super.fileChanged(resource, changeType, monitor) || isBuildFile(resource);
    }

    private IProjectImporter obtainBazelImporter() {
        return ExtensionsExtractor.<IProjectImporter> extractOrderedExtensions(IConstants.PLUGIN_ID, "importers")
                .stream().filter(importer -> importer instanceof BazelProjectImporter).findFirst().get();
    }

    @Override
    public String buildToolName() {
        return BUILD_TOOL_NAME;
    }

    @Override
    public boolean hasSpecificDeleteProjectLogic() {
        return true;
    }

    @Override
    public void deleteInvalidProjects(Collection<IPath> rootPaths, ArrayList<IProject> deleteProjectCandates,
            IProgressMonitor monitor) {
        for (IProject project : deleteProjectCandates) {
            if (applies(project)) {
                IPath projectLocation = getProjectLocation(project);

                if (ResourceUtils.isContainedIn(projectLocation, rootPaths)) {
                    LOG.info(project.getName() + " is contained in the root path, it's a valid project");
                } else {
                    try {
                        project.delete(false, true, monitor);
                    } catch (CoreException e1) {
                        JavaLanguageServerPlugin.logException(e1.getMessage(), e1);
                    }
                }
            }
        }
    }

    public static void calculateExcludedFilePatterns(String bazelWorkspaceRootDirectoryPath) {
        if (calculatedExcludedFilePatterns.isEmpty()) {

            EXCLUDED_FILE_PATTERN.stream().map(path -> StringUtils.join("**" + bazelWorkspaceRootDirectoryPath, path))
                    .forEach(calculatedExcludedFilePatterns::add);

        }
    }

    private IPath getProjectLocation(IProject project) {

        if (project.getFile(WORKSPACE_FILE_NAME).isAccessible()) {
            return project.getFile(WORKSPACE_FILE_NAME).getLocation();
        }

        if (project.getFile(WORKSPACE_FILE_NAME + BAZEL_FILE_NAME_SUFIX).isAccessible()) {
            return project.getFile(WORKSPACE_FILE_NAME + BAZEL_FILE_NAME_SUFIX).getLocation();
        }

        if (project.getFile(BUILD_FILE_NAME).isAccessible()) {
            return project.getFile(BUILD_FILE_NAME).getLocation();
        }

        if (project.getFile(BUILD_FILE_NAME + BAZEL_FILE_NAME_SUFIX).isAccessible()) {
            return project.getFile(BUILD_FILE_NAME + BAZEL_FILE_NAME_SUFIX).getLocation();
        }

        return project.getLocation();
    }
}
