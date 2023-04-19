/*-
 *
 */
package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BUILDPATH_PROBLEM_MARKER;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.CLASSPATH_CONTAINER_ID;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelElement;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelProjectFileSystemMapper;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaInfo.FileEntry;
import com.salesforce.bazel.sdk.command.BazelCQueryWithStarlarkExpressionCommand;

/**
 * Base class for provisioning strategies, providing common base logic re-usable by multiple strategies.
 */
public abstract class BaseProvisioningStrategy implements TargetProvisioningStrategy {

    private static Logger LOG = LoggerFactory.getLogger(BaseProvisioningStrategy.class);

    private BazelProjectFileSystemMapper fileSystemMapper;

    /**
     * Eclipse VM representing the current
     */
    protected IVMInstall javaToolchainVm;

    protected String javaToolchainSourceVersion;
    protected String javaToolchainTargetVersion;

    /**
     * Collects base Java information for a given project and the targets it represents.
     * <p>
     * This uses the target info from the model (as returned by <code>bazel query</code>) to discover source directories
     * and project level dependencies.
     * </p>
     * <p>
     * Note, the caller is responsible for supplying only targets mapped to the project. The behavior is undefined
     * otherwise and may result into non-working information.
     * </p>
     *
     * @param project
     *            the provisioned Bazel project (must not be <code>null</code>)
     * @param targets
     *            the list of targets to collect Java information for (must not be <code>null</code>)
     * @param monitor
     *            the progress monitor for checking cancellation (must not be <code>null</code>)
     *
     * @return the collected Java info (never <code>null</code>)
     * @throws CoreException
     */
    protected JavaInfo collectJavaInfo(BazelProject project, Collection<BazelTarget> targets, IProgressMonitor monitor)
            throws CoreException {
        // find common package
        var bazelPackage = expectCommonBazelPackage(targets);

        var javaInfo = new JavaInfo(bazelPackage);

        // process targets in the given order
        for (BazelTarget bazelTarget : targets) {
            javaInfo.addInfoFromTarget(bazelTarget);
        }

        // analyze for recommended project setup
        var recommendations = javaInfo.analyzeProjectRecommendations(monitor);

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} source directories: {}", targets,
                javaInfo.hasSourceDirectories() ? javaInfo.getSourceDirectories() : "n/a");
            LOG.debug("{} source files without root: {}", targets,
                javaInfo.hasSourceFilesWithoutCommonRoot() ? javaInfo.getSourceFilesWithoutCommonRoot() : "n/a");
        }

        // delete existing markers
        project.getProject().deleteMarkers(BUILDPATH_PROBLEM_MARKER, true, IResource.DEPTH_ZERO);

        // abort if canceled
        if (monitor.isCanceled()) {
            createBuildPathProblem(bazelPackage.getBazelWorkspace().getBazelProject(), Status.warning(
                "Bazel project provisioning for this workspace was canceled. The Eclipse workspace may not build properly."));
            throw new OperationCanceledException();
        }

        // create project level markers
        if (!recommendations.isOK()) {
            if (recommendations.isMultiStatus()) {
                for (IStatus status : recommendations.getChildren()) {
                    createBuildPathProblem(project, status);
                }
            } else {
                createBuildPathProblem(project, recommendations);
            }
        }

        return javaInfo;
    }

    /**
     * Configures the raw classpath for a project based on the {@link JavaInfo}.
     * <p>
     * This does not compute the classpath. Instead a classpath container is applied later to defer classpath
     * computation when project provisioning is completed for a workspace.
     * </p>
     *
     * @param project
     * @param javaInfo
     * @param progress
     * @throws CoreException
     */
    protected void configureRawClasspath(BazelProject project, JavaInfo javaInfo, IProgressMonitor progress)
            throws CoreException {
        List<IClasspathEntry> rawClasspath = new ArrayList<>();

        if (javaInfo.hasSourceFilesWithoutCommonRoot()) {
            rawClasspath
                    .add(JavaCore.newSourceEntry(getFileSystemMapper().getVirtualSourceFolder(project).getFullPath()));
        }

        if (javaInfo.hasSourceDirectories()) {
            for (FileEntry dir : javaInfo.getSourceDirectories()) {
                var sourceFolder = project.getProject().getFolder(dir.getPath());
                rawClasspath.add(JavaCore.newSourceEntry(sourceFolder.getFullPath()));
            }
        }

        rawClasspath.add(JavaCore.newContainerEntry(new Path(CLASSPATH_CONTAINER_ID)));

        if (javaToolchainVm != null) {
            rawClasspath.add(JavaCore.newContainerEntry(JavaRuntime.newJREContainerPath(javaToolchainVm)));
        } else {
            rawClasspath.add(JavaRuntime.getDefaultJREContainerEntry());
        }

        var javaProject = JavaCore.create(project.getProject());
        javaProject.setRawClasspath(rawClasspath.toArray(new IClasspathEntry[rawClasspath.size()]), true, progress);

        if (javaToolchainVm != null) {
            new JvmConfigurator().configureJVMSettings(javaProject, javaToolchainVm);
        }
        if (javaToolchainSourceVersion != null) {
            javaProject.setOption(JavaCore.COMPILER_SOURCE, javaToolchainSourceVersion);
        }
        if (javaToolchainTargetVersion != null) {
            javaProject.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, javaToolchainTargetVersion);
        }
    }

    /**
     * Creates a problem marker of type {@link BazelCoreSharedContstants#BUILDPATH_PROBLEM_MARKER} for the given status.
     *
     * @param project
     *            the project to create the marker at (must not be <code>null</code>)
     * @param status
     *            the status to create the marker for (must not be <code>null</code>)
     * @return the created marker (never <code>null</code>)
     * @throws CoreException
     */
    protected IMarker createBuildPathProblem(BazelProject project, IStatus status) throws CoreException {
        return createMarker(project.getProject(), BUILDPATH_PROBLEM_MARKER, status);
    }

    protected void createFolderAndParents(final IContainer folder, IProgressMonitor progress) throws CoreException {
        var monitor = SubMonitor.convert(progress, 2);
        try {
            if ((folder == null) || folder.exists()) {
                return;
            }

            if (!folder.getParent().exists()) {
                createFolderAndParents(folder.getParent(), monitor.newChild(1));
            }
            switch (folder.getType()) {
                case IResource.FOLDER:
                    ((IFolder) folder).create(IResource.NONE, true, monitor.newChild(1));
                    break;
                default:
                    throw new CoreException(Status.error(format("Cannot create resource '%s'", folder)));
            }
        } finally {
            progress.done();
        }
    }

    private IMarker createMarker(IResource resource, String type, IStatus status) throws CoreException {
        var message = status.getMessage();
        if (status.isMultiStatus()) {
            var children = status.getChildren();
            if ((children != null) && (children.length > 0)) {
                message = children[0].getMessage();
            }
        }
        if ((message == null) && (status.getException() != null)) {
            message = status.getException().getMessage();
        }

        Map<String, Object> markerAttributes = new HashMap<>();
        markerAttributes.put(IMarker.MESSAGE, message);
        markerAttributes.put(IMarker.SOURCE_ID, "Bazel Project Provisioning");

        if (status.matches(IStatus.ERROR)) {
            markerAttributes.put(IMarker.SEVERITY, Integer.valueOf(IMarker.SEVERITY_ERROR));
        } else if (status.matches(IStatus.WARNING)) {
            markerAttributes.put(IMarker.SEVERITY, Integer.valueOf(IMarker.SEVERITY_WARNING));
        } else if (status.matches(IStatus.INFO)) {
            markerAttributes.put(IMarker.SEVERITY, Integer.valueOf(IMarker.SEVERITY_INFO));
        }

        return resource.createMarker(type, markerAttributes);
    }

    /**
     * Creates an Eclipse project representing the specified owner.
     * <p>
     * The {@link BazelProject#PROJECT_PROPERTY_OWNER} property will be set to the given owner'S label.
     * </p>
     *
     * @param projectName
     *            the name of the project to create
     * @param owner
     *            the owner information
     * @param monitor
     *            monitor for progress reporting
     * @return the created Eclipse {@link IProject}
     * @throws CoreException
     */
    protected IProject createProjectForElement(String projectName, IPath projectLocation, BazelElement<?, ?> owner,
            IProgressMonitor progress) throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, 3);
            var projectDescription = getEclipseWorkspace().newProjectDescription(projectName);

            // place the project into the Bazel workspace project area
            projectDescription.setLocation(projectLocation);
            projectDescription.setComment(format("Bazel project representing '%s'", owner.getLabel()));

            // create project
            var project = getEclipseWorkspaceRoot().getProject(projectName);
            project.create(projectDescription, monitor.newChild(1));

            // open project
            project.open(monitor.newChild(1));

            // set natures separately in order to ensure they are configured properly
            projectDescription = project.getDescription();
            projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID, BAZEL_NATURE_ID });
            project.setDescription(projectDescription, monitor.newChild(1));

            // set properties
            project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT,
                getFileSystemMapper().getBazelWorkspace().getLocation().toString());
            project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_OWNER, owner.getLabel().getLabelPath());

            return project;
        } finally {
            progress.done();
        }
    }

    protected void deleteAllFilesNotInAllowList(IFolder folder, Set<IFile> allowList, IProgressMonitor progress)
            throws CoreException {
        try {
            Set<IFile> toRemove = new HashSet<>();
            folder.accept(r -> {
                if ((r.getType() == IResource.FILE) && !allowList.contains(r)) {
                    toRemove.add((IFile) r);
                }
                return true;
            });

            if (toRemove.isEmpty()) {
                return;
            }

            var monitor = SubMonitor.convert(progress, toRemove.size());
            for (IFile file : toRemove) {
                file.delete(true, monitor.newChild(1));
            }
        } finally {
            progress.done();
        }
    }

    /**
     * Queries <code>@bazel_tools//tools/jdk:current_java_toolchain</code> for extracting information about the default
     * Java toolchain used by the workspace.
     * <p>
     * After this method {@link #javaToolchainVm}, {@link #javaToolchainSourceVersion} and
     * {@link #javaToolchainTargetVersion} should be initialized.
     * </p>
     *
     * @param workspace
     *            the workspace for querying
     * @throws CoreException
     */
    protected void detectDefaultJavaToolchain(BazelWorkspace workspace) throws CoreException {
        var command = new BazelCQueryWithStarlarkExpressionCommand(workspace.getLocation().toFile().toPath(),
                "@bazel_tools//tools/jdk:current_java_toolchain",
                "providers(target)['JavaToolchainInfo'].source_version + '::' + providers(target)['JavaToolchainInfo'].target_version + '::' + providers(target)['JavaToolchainInfo'].java_runtime.java_home",
                false);
        var result = workspace.getCommandExecutor().runQueryWithoutLock(command);
        try {
            var tokenizer = new StringTokenizer(result, "::");
            javaToolchainSourceVersion = tokenizer.nextToken();
            javaToolchainTargetVersion = tokenizer.nextToken();
            var javaHome = tokenizer.nextToken();
            LOG.debug("source_level: {}, target_level: {}, java_home: {}", javaToolchainSourceVersion,
                javaToolchainTargetVersion, javaHome);

            // sanitize versions
            try {
                if (Integer.parseInt(javaToolchainSourceVersion) < 9) {
                    javaToolchainSourceVersion = "1." + javaToolchainSourceVersion;
                }
            } catch (NumberFormatException e) {
                throw new CoreException(Status
                        .error(format("Unable to detect Java Toolchain information. Error parsing source level (%s)",
                            javaToolchainSourceVersion), e));
            }
            try {
                if (Integer.parseInt(javaToolchainTargetVersion) < 9) {
                    javaToolchainTargetVersion = "1." + javaToolchainTargetVersion;
                }
            } catch (NumberFormatException e) {
                throw new CoreException(Status
                        .error(format("Unable to detect Java Toolchain information. Error parsing target level (%s)",
                            javaToolchainTargetVersion), e));
            }

            // resolve java home
            var resolvedJavaHomePath = java.nio.file.Path.of(javaHome);
            if (!resolvedJavaHomePath.isAbsolute()) {
                if (!javaHome.startsWith("external/")) {
                    throw new CoreException(Status.error(format(
                        "Unable to resolved java_home of '%s' into something meaningful. Please report as reproducible bug!",
                        javaHome)));
                }
                resolvedJavaHomePath = new BazelWorkspaceBlazeInfo(workspace).getOutputBase().resolve(javaHome);
            }

            javaToolchainVm = new JvmConfigurator().configureVMInstall(resolvedJavaHomePath, workspace);
        } catch (NoSuchElementException e) {
            throw new CoreException(Status.error(format(
                "Unable to detect Java Toolchain information. Error parsing output of bazel cquery (%s)", result), e));
        }
    }

    /**
     * Called by {@link #provisionProjectsForSelectedTargets(Collection, BazelWorkspace, IProgressMonitor)} after the
     * projects were created.
     * <p>
     * After all projects were created we go over them a second time to run the aspects and initialize the classpaths.
     * This is needed to allow proper wiring of dependencies to source projects in Eclipse.
     * </p>
     * <p>
     * The default implementation simply loops over all projects and calls
     * {@link #computeClasspath(BazelProject, BazelClasspathScope, IProgressMonitor)}. Sub classes may override if there
     * is a more efficient way of if this is not needed at all with a specific strategy.
     * </p>
     *
     * @param projects
     *            list of provisioned projects
     * @param progress
     *            monitor for reporting progress and checking cancellation
     * @throws CoreException
     */
    protected void doInitializeClasspaths(List<BazelProject> projects, IProgressMonitor progress) throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, "Initializing classpaths", projects.size());
            for (BazelProject bazelProject : projects) {
                computeClasspath(bazelProject, BazelClasspathScope.DEFAULT_CLASSPATH, monitor.newChild(1));
            }
        } finally {
            progress.done();
        }
    }

    /**
     * Called by {@link #provisionProjectsForSelectedTargets(Collection, BazelWorkspace, IProgressMonitor)} after base
     * workspace information has been detected.
     * <p>
     * Implementors are expected to map all of the targets into projects.
     * </p>
     *
     * @param targets
     *            collection of targets
     * @param progress
     *            monitor for reporting progress
     * @return list of provisioned projects
     * @throws CoreException
     */
    protected abstract List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets,
            IProgressMonitor progress) throws CoreException;

    protected BazelPackage expectCommonBazelPackage(Collection<BazelTarget> targets) throws CoreException {
        List<BazelPackage> allPackages =
                targets.stream().map(BazelTarget::getBazelPackage).distinct().collect(toList());
        if (allPackages.size() != 1) {
            throw new IllegalArgumentException(format(
                "Cannot process targets from different packages. Unable to detect common package. Expected 1 got %d.",
                allPackages.size()));
        }
        return allPackages.get(0); // BazelTarget::getBazelPackage guaranteed to not return null
    }

    protected IWorkspace getEclipseWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    protected IWorkspaceRoot getEclipseWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    /**
     * @return the {@link BazelProjectFileSystemMapper} (only set during
     *         {@link #provisionProjectsForSelectedTargets(Collection, BazelWorkspace, IProgressMonitor)})
     */
    protected BazelProjectFileSystemMapper getFileSystemMapper() {
        return requireNonNull(fileSystemMapper,
            "file system mapper not initialized, check code flow/implementation (likely a bug)");
    }

    /**
     * Creates Eclipse virtual files/folders for sources collected in the {@link JavaInfo}.
     *
     * @param project
     * @param javaInfo
     * @param progress
     * @throws CoreException
     */
    protected void linkSourcesIntoProject(BazelProject project, JavaInfo javaInfo, IProgressMonitor progress)
            throws CoreException {
        var monitor = SubMonitor.convert(progress, 100);
        try {
            if (javaInfo.hasSourceFilesWithoutCommonRoot()) {
                var virtualSourceFolder = getFileSystemMapper().getVirtualSourceFolder(project);
                if (!virtualSourceFolder.exists()) {
                    virtualSourceFolder.create(IResource.NONE, true, monitor.newChild(1));
                }
                var files = javaInfo.getSourceFilesWithoutCommonRoot();
                Set<IFile> linkedFiles = new HashSet<>();
                for (FileEntry fileEntry : files) {
                    // peek at Java package to find proper "root"
                    var packagePath = fileEntry.getDetectedPackagePath();
                    var packageFolder = virtualSourceFolder.getFolder(packagePath);
                    if (!packageFolder.exists()) {
                        createFolderAndParents(packageFolder, monitor.newChild(1));
                    }

                    // create link to file
                    var file = packageFolder.getFile(fileEntry.getPath().lastSegment());
                    file.createLink(fileEntry.getLocation(), IResource.REPLACE, monitor.newChild(1));

                    // remember for cleanup
                    linkedFiles.add(file);
                }

                // remove all files not created as part of this loop
                deleteAllFilesNotInAllowList(virtualSourceFolder, linkedFiles, monitor.newChild(1));
            }

            if (javaInfo.hasSourceDirectories()) {
                var directories = javaInfo.getSourceDirectories();
                NEXT_FOLDER: for (FileEntry dir : directories) {
                    var sourceFolder = project.getProject().getFolder(dir.getPath());
                    if (sourceFolder.exists() && !sourceFolder.isLinked()) {
                        // check if there is any linked parent we can remove
                        var parent = sourceFolder.getParent();
                        while ((parent != null) && (parent.getType() != IResource.PROJECT)) {
                            if (parent.isLinked()) {
                                parent.delete(true, monitor.newChild(1));
                                break;
                            }
                            parent = parent.getParent();
                        }
                        if (sourceFolder.exists()) {
                            // TODO create problem marker
                            continue NEXT_FOLDER;
                        }
                    }

                    // ensure the parent exists
                    if (!sourceFolder.getParent().exists()) {
                        createFolderAndParents(sourceFolder.getParent(), monitor.newChild(1));
                    }

                    // create link to folder
                    sourceFolder.createLink(dir.getLocation(), IResource.REPLACE, monitor.newChild(1));
                }
            }

            // ensure the BUILD file is linked
            var buildFileLocation = javaInfo.getBazelPackage().getBuildFileLocation();
            var buildFile = project.getProject().getFile(buildFileLocation.lastSegment());
            if (buildFile.exists() && !buildFile.isLinked()) {
                buildFile.delete(true, monitor.newChild(1));
            }
            buildFile.createLink(buildFileLocation, IResource.REPLACE, monitor.newChild(1));
        } finally {
            progress.done();
        }
    }

    @Override
    public List<BazelProject> provisionProjectsForSelectedTargets(Collection<BazelTarget> targets,
            BazelWorkspace workspace, IProgressMonitor progress) throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, "Provisioning projects", targets.size());

            // ensure there is a mapper
            fileSystemMapper = new BazelProjectFileSystemMapper(workspace);

            // cleanup markers at workspace level
            workspace.getBazelProject().getProject().deleteMarkers(BUILDPATH_PROBLEM_MARKER, true,
                IResource.DEPTH_ZERO);

            // detect default Java level
            detectDefaultJavaToolchain(workspace);

            // create projects
            var result = doProvisionProjects(targets, monitor);

            // after provisioning we go over the projects a second time to initialize the classpaths
            doInitializeClasspaths(result, monitor);

            // done
            return result;
        } finally {
            progress.done();
        }
    }

}
