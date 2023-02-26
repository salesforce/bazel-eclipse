package com.salesforce.bazel.eclipse.jdtls.managers;

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
import org.slf4j.Logger;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;

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

    private static Logger LOG = org.slf4j.LoggerFactory.getLogger(BazelBuildSupport.class);

    public static void calculateExcludedFilePatterns(String bazelWorkspaceRootDirectoryPath) {
        if (calculatedExcludedFilePatterns.isEmpty()) {

            EXCLUDED_FILE_PATTERN.stream().map(path -> StringUtils.join("**" + bazelWorkspaceRootDirectoryPath, path))
                    .forEach(calculatedExcludedFilePatterns::add);

        }
    }

    @Override
    public boolean applies(IProject project) {
        try {
            return (project != null) && project.hasNature(BazelCoreSharedContstants.BAZEL_NATURE_ID);

        } catch (CoreException e) {
            return false;
        }
    }

    @Override
    public String buildToolName() {
        return BUILD_TOOL_NAME;
    }

    @Override
    public void deleteInvalidProjects(Collection<IPath> rootPaths, ArrayList<IProject> deleteProjectCandates,
            IProgressMonitor monitor) {
        for (IProject project : deleteProjectCandates) {
            if (applies(project)) {
                var projectLocation = getProjectLocation(project);

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

    @Override
    public boolean fileChanged(IResource resource, CHANGE_TYPE changeType, IProgressMonitor monitor)
            throws CoreException {
        if ((resource == null) || !applies(resource.getProject())) {
            return false;
        }
        return IBuildSupport.super.fileChanged(resource, changeType, monitor) || isBuildFile(resource);
    }

    @Override
    public List<String> getExcludedFilePatterns() {
        return calculatedExcludedFilePatterns;
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

    @Override
    public List<String> getWatchPatterns() {
        return WATCH_FILE_PATTERNS;
    }

    @Override
    public boolean hasSpecificDeleteProjectLogic() {
        return true;
    }

    @Override
    public boolean isBuildFile(IResource resource) {
        return (resource != null) && (resource.getProject() != null) && (resource.getType() == IResource.FILE)
                && (resource.getName().endsWith(BAZELPROJECT_FILE_NAME_SUFIX)
                        || resource.getName().endsWith(BAZEL_FILE_NAME_SUFIX)
                        || BUILD_FILE_NAME.equals(resource.getName())
                        || WORKSPACE_FILE_NAME.equals(resource.getName()));
    }

    @Override
    public boolean isBuildLikeFileName(String fileName) {
        return fileName.endsWith(BAZELPROJECT_FILE_NAME_SUFIX) || BUILD_FILE_NAME.equals(fileName)
                || WORKSPACE_FILE_NAME.equals(fileName) || fileName.endsWith(BAZEL_FILE_NAME_SUFIX);
    }

    private IProjectImporter obtainBazelImporter() {
        return ExtensionsExtractor.<IProjectImporter> extractOrderedExtensions(IConstants.PLUGIN_ID, "importers")
                .stream().filter(importer -> importer instanceof BazelProjectImporter).findFirst().get();
    }

    @Override
    public void update(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
        try {
            LOG.debug("BJLS update project {}", project.getName());
            updateInternal(project, force, monitor);

        } catch (CoreException ex) {
            throw ex;

        } catch (AssertionFailedException | NoSuchElementException ex) {
            // No bazel importers found. This is definitely illegal situation
            // which is not clear how to solve right now.
            // Just skip it.
        }

    }

    protected void updateInternal(IProject project, boolean force, IProgressMonitor monitor) throws CoreException {
        LOG.debug("updateInternal {}", project.getName());

        Assert.isTrue(applies(project));

        final var importer = obtainBazelImporter();

        importer.initialize(ComponentContext.getInstance().getBazelWorkspace().getBazelWorkspaceRootDirectory());

        var bazelCommandManager = ComponentContext.getInstance().getBazelCommandManager();
        var bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(ComponentContext.getInstance().getBazelWorkspace());

        bazelWorkspaceCmdRunner.flushAspectInfoCache();

        Assert.isTrue(importer.applies(monitor));

        importer.importToWorkspace(monitor);
    }
}
