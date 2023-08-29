/*-
 *
 */
package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BUILDPATH_PROBLEM_MARKER;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.CLASSPATH_CONTAINER_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.PROBLEM_MARKER;
import static com.salesforce.bazel.eclipse.core.model.discovery.EclipsePreferencesHelper.convertToPreferences;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.eclipse.core.runtime.Platform.PI_RUNTIME;
import static org.eclipse.core.runtime.Platform.PREF_LINE_SEPARATOR;
import static org.eclipse.core.runtime.SubMonitor.SUPPRESS_NONE;
import static org.eclipse.jdt.core.IClasspathAttribute.ADD_EXPORTS;
import static org.eclipse.jdt.core.IClasspathAttribute.ADD_OPENS;
import static org.eclipse.jdt.core.IClasspathAttribute.MODULE;
import static org.eclipse.jdt.core.JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM;
import static org.eclipse.jdt.core.JavaCore.COMPILER_RELEASE;
import static org.eclipse.jdt.core.JavaCore.COMPILER_SOURCE;
import static org.eclipse.jdt.core.JavaCore.DISABLED;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.osgi.service.prefs.BackingStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;
import com.salesforce.bazel.eclipse.core.model.BazelElement;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelProjectFileSystemMapper;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaResourceInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceInfo;
import com.salesforce.bazel.sdk.command.BazelCQueryWithStarlarkExpressionCommand;

/**
 * Base class for provisioning strategies, providing common base logic re-usable by multiple strategies.
 */
public abstract class BaseProvisioningStrategy implements TargetProvisioningStrategy {

    private static final String FILE_EXTENSION_DOT_PREFS = ".prefs";

    private static final String JAVAC_OPT_ADD_OPENS = "--add-opens";

    private static final String JAVAC_OPT_ADD_EXPORTS = "--add-exports";

    private static final IClasspathAttribute CLASSPATH_ATTRIBUTE_FOR_TEST =
            JavaCore.newClasspathAttribute(IClasspathAttribute.TEST, Boolean.TRUE.toString());

    private static final IPath[] EXCLUDE_JAVA_SOURCE = { IPath.forPosix("**/*.java") };

    private static Logger LOG = LoggerFactory.getLogger(BaseProvisioningStrategy.class);

    private BazelProjectFileSystemMapper fileSystemMapper;

    /**
     * Eclipse VM representing the current
     */
    protected IVMInstall javaToolchainVm;

    protected String javaToolchainSourceVersion;
    protected String javaToolchainTargetVersion;

    private JvmConfigurator jvmConfigurator;

    /**
     * Adds all information from a {@link BazelTarget} to the {@link JavaProjectInfo}.
     *
     * @param target
     * @throws CoreException
     */
    private void addInfoFromTarget(JavaProjectInfo javaInfo, BazelTarget bazelTarget) throws CoreException {
        var isTestTarget = isTestTarget(bazelTarget);

        var attributes = bazelTarget.getRuleAttributes();
        var srcs = attributes.getStringList("srcs");
        if (srcs != null) {
            for (String src : srcs) {
                if (isTestTarget) {
                    javaInfo.addTestSrc(src);
                } else {
                    javaInfo.addSrc(src);
                }
            }
        }

        var resources = attributes.getStringList("resources");
        if (resources != null) {
            var resourceStripPrefix = attributes.getString("resource_strip_prefix");
            for (String resource : resources) {
                if (isTestTarget) {
                    javaInfo.addTestResource(resource, resourceStripPrefix);
                } else {
                    javaInfo.addResource(resource, resourceStripPrefix);
                }
            }
        }

        var pluginDeps = attributes.getStringList("plugins");
        if (pluginDeps != null) {
            for (String dep : pluginDeps) {
                javaInfo.addPluginDep(dep);
            }
        }

        var javacOpts = attributes.getStringList("javacopts");
        if (javacOpts != null) {
            for (String javacOpt : javacOpts) {
                javaInfo.addJavacOpt(javacOpt);
            }
        }
    }

    private void addResourceFolders(BazelProject project, List<IClasspathEntry> rawClasspath,
            JavaResourceInfo resourceInfo, boolean useTestsClasspath) {
        var virtualResourceFolder = useTestsClasspath ? getFileSystemMapper().getVirtualResourceFolderForTests(project)
                : getFileSystemMapper().getVirtualResourceFolder(project);
        var outputLocation = useTestsClasspath ? getFileSystemMapper().getOutputFolderForTests(project).getFullPath()
                : getFileSystemMapper().getOutputFolder(project).getFullPath();
        var classpathAttributes = useTestsClasspath ? new IClasspathAttribute[] { CLASSPATH_ATTRIBUTE_FOR_TEST }
                : new IClasspathAttribute[] {};
        if (resourceInfo.hasResourceFilesWithoutCommonRoot()) {
            // add the virtual folder for resources
            rawClasspath.add(
                JavaCore.newSourceEntry(
                    virtualResourceFolder.getFullPath(),
                    null /* include all */,
                    EXCLUDE_JAVA_SOURCE,
                    outputLocation,
                    classpathAttributes));
        }
        if (resourceInfo.hasResourceDirectories()) {
            for (IPath dir : resourceInfo.getResourceDirectories()) {
                // when the directory is empty, the virtual "srcs" container must be used
                // this logic here requires proper linking support in linkSourcesIntoProject method
                var resourceFolder = dir.isEmpty() ? virtualResourceFolder : project.getProject().getFolder(dir);
                if (!resourceFolder.exists()) {
                    LOG.debug("Ignoring none existing resource folder: {}", resourceFolder);
                    continue;
                }
                if (rawClasspath.stream()
                        .anyMatch(
                            e -> (e.getEntryKind() == IClasspathEntry.CPE_SOURCE)
                                    && e.getPath().equals(resourceFolder.getFullPath()))) {
                    LOG.debug("Ignoring duplicate resource folder: {}", resourceFolder);
                    continue;
                }
                var inclusionPatterns = resourceInfo.getInclusionPatternsForSourceDirectory(dir);
                var exclusionPatterns = resourceInfo.getExclutionPatternsForSourceDirectory(dir);
                if ((exclusionPatterns == null) || (exclusionPatterns.length == 0)) {
                    exclusionPatterns = EXCLUDE_JAVA_SOURCE; // exclude all .java files by default
                }
                rawClasspath.add(
                    JavaCore.newSourceEntry(
                        resourceFolder.getFullPath(),
                        inclusionPatterns,
                        exclusionPatterns,
                        outputLocation,
                        classpathAttributes));
            }
        }
    }

    private void addSourceFolders(BazelProject project, List<IClasspathEntry> rawClasspath,
            JavaSourceInfo javaSourceInfo, boolean useTestsClasspath) throws CoreException {
        var virtualSourceFolder = useTestsClasspath ? getFileSystemMapper().getVirtualSourceFolderForTests(project)
                : getFileSystemMapper().getVirtualSourceFolder(project);
        var outputLocation = useTestsClasspath ? getFileSystemMapper().getOutputFolderForTests(project).getFullPath()
                : getFileSystemMapper().getOutputFolder(project).getFullPath();
        var classpathAttributes = useTestsClasspath ? new IClasspathAttribute[] { CLASSPATH_ATTRIBUTE_FOR_TEST }
                : new IClasspathAttribute[] {};
        if (javaSourceInfo.hasSourceFilesWithoutCommonRoot()) {
            // add the virtual folder for resources
            rawClasspath.add(
                JavaCore.newSourceEntry(
                    virtualSourceFolder.getFullPath(),
                    null /* include all */,
                    null /* exclude nothing */,
                    outputLocation,
                    classpathAttributes));
        }
        if (javaSourceInfo.hasSourceDirectories()) {
            for (IPath dir : javaSourceInfo.getSourceDirectories()) {
                // when the directory is empty, the virtual "srcs" container must be used
                // this logic here requires proper linking support in linkSourcesIntoProject method
                var sourceFolder = dir.isEmpty() ? virtualSourceFolder : project.getProject().getFolder(dir);
                var inclusionPatterns = javaSourceInfo.getInclusionPatternsForSourceDirectory(dir);
                var exclusionPatterns = javaSourceInfo.getExclutionPatternsForSourceDirectory(dir);
                var existingEntry =
                        rawClasspath.stream().anyMatch(entry -> entry.getPath().equals(sourceFolder.getFullPath()));
                if (existingEntry) {
                    if (useTestsClasspath) {
                        createBuildPathProblem(
                            project,
                            Status.warning(
                                format(
                                    "Folder '%s' found twice on the classpath. This is likely because it's used as test as well as non-test source. Please consider modifying the project setup!",
                                    sourceFolder)));
                    } else {
                        createBuildPathProblem(
                            project,
                            Status.error(
                                format(
                                    "Folder '%s' found twice on the classpath. This is an unexpected situation. Please consider modifying the project setup! Don't hesitate and reach out for help.",
                                    sourceFolder)));
                    }
                } else {
                    rawClasspath.add(
                        JavaCore.newSourceEntry(
                            sourceFolder.getFullPath(),
                            inclusionPatterns,
                            exclusionPatterns,
                            outputLocation,
                            classpathAttributes));
                }

            }
        }
    }

    /**
     * Calls {@link JavaProjectInfo#analyzeProjectRecommendations(IProgressMonitor)} and creates problem markers for any
     * identified issue.
     *
     * @param project
     *            the project
     * @param javaInfo
     *            the populated project info
     * @param monitor
     *            monitor for cancellation check
     * @throws CoreException
     *             in case of problems creating the marker
     */
    protected void analyzeProjectInfo(BazelProject project, JavaProjectInfo javaInfo, IProgressMonitor monitor)
            throws CoreException {
        // analyze for recommended project setup
        var recommendations = javaInfo.analyzeProjectRecommendations(monitor);

        if (LOG.isDebugEnabled()) {
            var sourceInfo = javaInfo.getSourceInfo();
            LOG.debug(
                "{} source directories: {}",
                project,
                sourceInfo.hasSourceDirectories() ? sourceInfo.getSourceDirectories() : "n/a");
            LOG.debug(
                "{} source files without root: {}",
                project,
                sourceInfo.hasSourceFilesWithoutCommonRoot() ? sourceInfo.getSourceFilesWithoutCommonRoot() : "n/a");

            var testSourceInfo = javaInfo.getSourceInfo();
            LOG.debug(
                "{} test source directories: {}",
                project,
                testSourceInfo.hasSourceDirectories() ? testSourceInfo.getSourceDirectories() : "n/a");
            LOG.debug(
                "{} test source files without root: {}",
                project,
                testSourceInfo.hasSourceFilesWithoutCommonRoot() ? testSourceInfo.getSourceFilesWithoutCommonRoot()
                        : "n/a");
        }

        // delete existing markers
        project.getProject().deleteMarkers(PROBLEM_MARKER, true, IResource.DEPTH_ZERO);

        // abort if canceled
        if (monitor.isCanceled()) {
            createBuildPathProblem(
                project,
                Status.warning(
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
    }

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
    protected JavaProjectInfo collectJavaInfo(BazelProject project, Collection<BazelTarget> targets,
            IProgressMonitor monitor) throws CoreException {
        // find common package
        var bazelPackage = expectCommonBazelPackage(targets);

        var javaInfo = new JavaProjectInfo(bazelPackage);

        // process targets in the given order
        for (BazelTarget bazelTarget : targets) {
            addInfoFromTarget(javaInfo, bazelTarget);
        }

        analyzeProjectInfo(project, javaInfo, monitor);

        return javaInfo;
    }

    /**
     * Configures the raw classpath for a project based on the {@link JavaProjectInfo}.
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
    protected void configureRawClasspath(BazelProject project, JavaProjectInfo javaInfo, IProgressMonitor progress)
            throws CoreException {
        List<IClasspathEntry> rawClasspath = new ArrayList<>();

        addSourceFolders(project, rawClasspath, javaInfo.getSourceInfo(), false /* useTestsClasspath */);
        addResourceFolders(project, rawClasspath, javaInfo.getResourceInfo(), false /* useTestsClasspath */);

        addSourceFolders(project, rawClasspath, javaInfo.getTestSourceInfo(), true /* useTestsClasspath */);
        addResourceFolders(project, rawClasspath, javaInfo.getTestResourceInfo(), true /* useTestsClasspath*/);

        rawClasspath.add(JavaCore.newContainerEntry(new Path(CLASSPATH_CONTAINER_ID)));

        var javaProject = JavaCore.create(project.getProject());

        // apply settings configured in project view
        copyProjectSettings(project.getProject(), project.getBazelWorkspace());

        // tweak to current JVMconfiguration
        getJvmConfigurator()
                .applyJavaProjectOptions(javaProject, javaToolchainSourceVersion, javaToolchainTargetVersion, null);

        var extraAttributesForJdk = getExtraJvmAttributes(javaInfo);
        var executionEnvironmentId = getJvmConfigurator().getExecutionEnvironmentId(javaProject);
        if (executionEnvironmentId != null) {
            // prefer setting EE based JDK for compilation
            rawClasspath.add(
                getJvmConfigurator().getJreClasspathContainerForExecutionEnvironment(
                    executionEnvironmentId,
                    extraAttributesForJdk));
        } else if (javaToolchainVm != null) {
            // use toolchain specific entry
            rawClasspath.add(
                JavaCore.newContainerEntry(
                    JavaRuntime.newJREContainerPath(javaToolchainVm),
                    null /* no access rules */,
                    extraAttributesForJdk,
                    false /* not exported */));

        } else {
            rawClasspath.add(
                JavaCore.newContainerEntry(
                    JavaRuntime.getDefaultJREContainerEntry().getPath(),
                    null /* no access rules */,
                    extraAttributesForJdk,
                    false /* not exported */));
        }

        // if the classpath has no source folder Eclipse will default to the whole project
        // this is not good for us because this could cause duplication of an entire hierarchy
        // we therefore ensure there is a default folder
        if (!rawClasspath.stream().anyMatch(e -> e.getEntryKind() == IClasspathEntry.CPE_SOURCE)) {
            // add the virtual folder for resources
            var virtualSourceFolder = getFileSystemMapper().getVirtualSourceFolder(project);
            createFolderAndParents(virtualSourceFolder, progress);
            rawClasspath.add(
                JavaCore.newSourceEntry(
                    virtualSourceFolder.getFullPath(),
                    null /* include all */,
                    null /* exclude nothing */,
                    getFileSystemMapper().getOutputFolder(project).getFullPath(),
                    null /* nothing */));
        }

        javaProject.setRawClasspath(rawClasspath.toArray(new IClasspathEntry[rawClasspath.size()]), true, progress);

        if (javaToolchainVm != null) {
            getJvmConfigurator().configureJVMSettings(javaProject, javaToolchainVm);
        }
        if (javaToolchainSourceVersion != null) {
            javaProject.setOption(COMPILER_SOURCE, javaToolchainSourceVersion);
        }
        if (javaToolchainTargetVersion != null) {
            javaProject.setOption(COMPILER_CODEGEN_TARGET_PLATFORM, javaToolchainTargetVersion);
        }

        // if we have add-opens or add-export we need to turn release flag off
        // (https://stackoverflow.com/questions/45370178/exporting-a-package-from-system-module-is-not-allowed-with-release)
        if (Stream.of(extraAttributesForJdk)
                .anyMatch(a -> a.getName().equals(ADD_OPENS) || a.getName().equals(ADD_EXPORTS))) {
            javaProject.setOption(COMPILER_RELEASE, DISABLED);
        }

    }

    /**
     * Copies project settings from the workspace project into the target project.
     * <p>
     * This ensures consistent settings across all projects of the same Bazel workspace.
     * </p>
     *
     * @param target
     *            the target project
     * @param bazelWorkspace
     *            the workspace suppliying the settings
     * @throws CoreException
     */
    protected void copyProjectSettings(IProject target, BazelWorkspace bazelWorkspace) throws CoreException {
        var projectSettings = bazelWorkspace.getBazelProjectView().projectSettings();
        if ((projectSettings == null) || projectSettings.isEmpty()) {
            return;
        }

        var targetPreferences = getPreferences(target.getProject());

        for (WorkspacePath projectSettingsFile : projectSettings) {
            var srcPreferencesFile = bazelWorkspace.getLocation().toPath().resolve(projectSettingsFile.asPath());
            copyProjectSettingsToProjectPreferences(srcPreferencesFile, targetPreferences);
        }
    }

    private void copyProjectSettingsToProjectPreferences(java.nio.file.Path srcPreferencesFile,
            IEclipsePreferences targetPreferences) throws CoreException {
        var fileName = srcPreferencesFile.getFileName().toString();
        if (!fileName.endsWith(FILE_EXTENSION_DOT_PREFS)) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Invalid project settings file '%s' specified in project view. File name must follow Eclipse project settings nameing (eg., 'org.eclipse.jdt.core.prefs').",
                            fileName)));
        }

        // this re-implements lots of functionality from Eclipse ProjectPreferences
        try (var in = Files.newBufferedReader(srcPreferencesFile);) {
            var nodeName = fileName.substring(0, fileName.length() - FILE_EXTENSION_DOT_PREFS.length());
            var srcPreferences = new Properties();
            srcPreferences.load(in);
            var targetNode = targetPreferences.node(nodeName);
            convertToPreferences(srcPreferences, targetNode);
            targetNode.flush();
        } catch (BackingStoreException e) {
            throw new CoreException(Status.error("Error coping preferences from setting file project.", e));
        } catch (IOException e) {
            throw new CoreException(
                    Status.error(
                        format("Error reading project settings file '%s' specified in project view.", fileName),
                        e));
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

    /**
     * Creates a folder hierarchy and marks them as derived (because they are generated and should not go into SCM)
     *
     * @param folder
     * @param progress
     * @throws CoreException
     */
    protected final void createFolderAndParents(final IContainer folder, IProgressMonitor progress)
            throws CoreException {
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
                    ((IFolder) folder).create(IResource.FORCE | IResource.DERIVED, true, monitor.newChild(1));
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
     * If a project already exists for the given location it will be used instead. The
     * {@link BazelProject#PROJECT_PROPERTY_OWNER} property will be set to the given owner's label.
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
            SubMonitor monitor) throws CoreException {
        monitor.setWorkRemaining(5);

        // locate existing project by its location (never use the name)
        var project = findProjectForLocation(projectLocation);

        // open existing
        if ((project != null) && !project.isOpen()) {
            try {
                project.open(monitor.split(1, SUPPRESS_NONE));
            } catch (CoreException e) {
                LOG.warn("Unable to open existing project '{}'. Deleting and re-creating the project.", project, e);
                project.delete(true, monitor.split(1, SUPPRESS_NONE));
                project = null;
            }
        }

        // check for name collection
        if (project == null) {
            project = getEclipseWorkspaceRoot().getProject(projectName);
            if (project.exists()) {
                LOG.warn(
                    "Found existing project with name'{}' at different location. Deleting and re-creating the project.",
                    project);
                project.delete(true, monitor.split(1, SUPPRESS_NONE));
            }

            // create new project
            var projectDescription = getEclipseWorkspace().newProjectDescription(projectName);
            projectDescription.setLocation(projectLocation);
            projectDescription.setComment(format("Bazel project representing '%s'", owner.getLabel()));
            project.create(projectDescription, monitor.split(1, SUPPRESS_NONE));

            // ensure project is open (creating a project which failed opening previously will create a closed project)
            if (!project.isOpen()) {
                project.open(monitor.split(1, SUPPRESS_NONE));
            }
        } else {
            // open existing
            if (!project.isOpen()) {
                project.open(monitor.split(1, SUPPRESS_NONE));
            }

            // fix name
            if (!projectName.equals(project.getName())) {
                var projectDescription = project.getDescription();
                projectDescription.setName(projectName);
                projectDescription.setComment(format("Bazel project representing '%s'", owner.getLabel()));
                project.move(projectDescription, true, monitor.split(1, SUPPRESS_NONE));
            }
        }

        // set natures separately in order to ensure they are configured properly
        var projectDescription = project.getDescription();
        projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID, BAZEL_NATURE_ID });
        project.setDescription(projectDescription, monitor.newChild(1));

        // set properties
        project.setPersistentProperty(
            BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT,
            getFileSystemMapper().getBazelWorkspace().getLocation().toString());
        project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_OWNER, owner.getLabel().getLabelPath());

        // set encoding to UTF-8
        project.setDefaultCharset(StandardCharsets.UTF_8.name(), monitor.split(1));

        // set line separator to posix
        setLineSeparator(getPreferences(project), "\n");

        return project;
    }

    private void deleteAllFilesMatchingPredicate(IFolder root, Predicate<IFile> selector, IProgressMonitor progress)
            throws CoreException {
        try {
            Set<IFile> toRemove = new HashSet<>();
            root.accept(r -> {
                if ((r.getType() == IResource.FILE) && selector.test((IFile) r)) {
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

    protected void deleteAllFilesNotInAllowList(IFolder root, Set<IFile> allowList, IProgressMonitor progress)
            throws CoreException {
        deleteAllFilesMatchingPredicate(root, not(allowList::contains), progress);
    }

    protected void deleteAllFilesNotInFolderList(IFolder root, IFolder folderToKeep, IProgressMonitor progress)
            throws CoreException {
        var folderLocation =
                requireNonNull(folderToKeep.getLocation(), () -> format("folder '%s' has no location", folderToKeep));
        deleteAllFilesMatchingPredicate(
            root,
            f -> (f.getLocation() != null) && !folderLocation.isPrefixOf(f.getLocation()),
            progress);
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
        var command = new BazelCQueryWithStarlarkExpressionCommand(
                workspace.getLocation().toPath(),
                "@bazel_tools//tools/jdk:current_java_toolchain",
                "providers(target)['JavaToolchainInfo'].source_version + '::' + providers(target)['JavaToolchainInfo'].target_version + '::' + providers(target)['JavaToolchainInfo'].java_runtime.java_home",
                false,
                "Querying for Java toolchain information");
        var result = workspace.getCommandExecutor().runQueryWithoutLock(command).trim();
        try {
            var tokenizer = new StringTokenizer(result, "::");
            javaToolchainSourceVersion = tokenizer.nextToken();
            javaToolchainTargetVersion = tokenizer.nextToken();
            var javaHome = tokenizer.nextToken();
            LOG.debug(
                "source_level: {}, target_level: {}, java_home: {}",
                javaToolchainSourceVersion,
                javaToolchainTargetVersion,
                javaHome);

            // sanitize versions
            try {
                if (Integer.parseInt(javaToolchainSourceVersion) < 9) {
                    javaToolchainSourceVersion = "1." + javaToolchainSourceVersion;
                }
            } catch (NumberFormatException e) {
                throw new CoreException(
                        Status.error(
                            format(
                                "Unable to detect Java Toolchain information. Error parsing source level (%s)",
                                javaToolchainSourceVersion),
                            e));
            }
            try {
                if (Integer.parseInt(javaToolchainTargetVersion) < 9) {
                    javaToolchainTargetVersion = "1." + javaToolchainTargetVersion;
                }
            } catch (NumberFormatException e) {
                throw new CoreException(
                        Status.error(
                            format(
                                "Unable to detect Java Toolchain information. Error parsing target level (%s)",
                                javaToolchainTargetVersion),
                            e));
            }

            // resolve java home
            var resolvedJavaHomePath = java.nio.file.Path.of(javaHome);
            if (!resolvedJavaHomePath.isAbsolute()) {
                if (!javaHome.startsWith("external/")) {
                    throw new CoreException(
                            Status.error(
                                format(
                                    "Unable to resolved java_home of '%s' into something meaningful. Please report as reproducible bug!",
                                    javaHome)));
                }
                resolvedJavaHomePath = new BazelWorkspaceBlazeInfo(workspace).getOutputBase().resolve(javaHome);
            }

            javaToolchainVm = getJvmConfigurator().configureVMInstall(resolvedJavaHomePath, workspace);
        } catch (NoSuchElementException e) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Unable to detect Java Toolchain information. Error parsing output of bazel cquery (%s)",
                            result),
                        e));
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
     * {@link #computeClasspaths(Collection, BazelWorkspace, BazelClasspathScope, IProgressMonitor)}. Sub classes may
     * override if there is a more efficient way of if this is not needed at all with a specific strategy.
     * </p>
     *
     * @param projects
     *            list of provisioned projects
     * @param workspace
     *            the workspace
     * @param monitor
     *            monitor for reporting progress and checking cancellation
     * @throws CoreException
     */
    protected void doInitializeClasspaths(List<BazelProject> projects, BazelWorkspace workspace, SubMonitor monitor)
            throws CoreException {
        try {
            // use the job to properly trigger the classpath manager
            new InitializeOrRefreshClasspathJob(
                    projects.stream(),
                    workspace.getParent().getModelManager().getClasspathManager(),
                    true).runInWorkspace(monitor);
        } finally {
            monitor.done();
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
     * @param monitor
     *            monitor for reporting progress
     * @return list of provisioned projects
     * @throws CoreException
     */
    protected abstract List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets, SubMonitor monitor)
            throws CoreException;

    protected BazelPackage expectCommonBazelPackage(Collection<BazelTarget> targets) throws CoreException {
        List<BazelPackage> allPackages =
                targets.stream().map(BazelTarget::getBazelPackage).distinct().collect(toList());
        if (allPackages.size() != 1) {
            throw new IllegalArgumentException(
                    format(
                        "Cannot process targets from different packages. Unable to detect common package. Expected 1 got %d.",
                        allPackages.size()));
        }
        return allPackages.get(0); // BazelTarget::getBazelPackage guaranteed to not return null
    }

    private IPath findCommonParentPackagePrefix(Collection<IPath> detectedJavaPackagesForSourceDirectory) {
        if (detectedJavaPackagesForSourceDirectory.isEmpty()) {
            return null;
        }

        if (detectedJavaPackagesForSourceDirectory.size() == 1) {
            return detectedJavaPackagesForSourceDirectory.stream().findFirst().get();
        }

        Set<IPath> possiblePrefixes = new LinkedHashSet<>(detectedJavaPackagesForSourceDirectory);
        for (IPath prefix : possiblePrefixes) {
            if (detectedJavaPackagesForSourceDirectory.stream().allMatch(p -> prefix.isPrefixOf(p))) {
                // abort early, we have a common prefix and there can really only be one
                return prefix;
            }
        }

        return null;
    }

    private IProject findProjectForLocation(IPath location) {
        var potentialProjects = getEclipseWorkspaceRoot().findContainersForLocationURI(URIUtil.toURI(location));

        // first valid project wins
        for (IContainer potentialProject : potentialProjects) {
            if (potentialProject.getType() != IResource.PROJECT) {
                continue;
            }
            return (IProject) potentialProject;
        }

        return null;
    }

    protected IWorkspace getEclipseWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    protected IWorkspaceRoot getEclipseWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    private IClasspathAttribute[] getExtraJvmAttributes(JavaProjectInfo javaInfo) {
        Set<String> addExports = new LinkedHashSet<>();
        Set<String> addOpens = new LinkedHashSet<>();
        for (String opt : javaInfo.getJavacOpts()) {
            opt = opt.trim();
            if (opt.startsWith(JAVAC_OPT_ADD_OPENS)) {
                addOpens.add(getOptionValue(opt, JAVAC_OPT_ADD_OPENS));
            } else if (opt.startsWith(JAVAC_OPT_ADD_EXPORTS)) {
                addExports.add(getOptionValue(opt, JAVAC_OPT_ADD_EXPORTS));
            }
        }

        List<IClasspathAttribute> result = new ArrayList<>();
        if ((addExports.size() + addOpens.size()) > 0) {
            result.add(JavaCore.newClasspathAttribute(MODULE, TRUE.toString()));
            if (addExports.size() > 0) {
                result.add(JavaCore.newClasspathAttribute(ADD_EXPORTS, addExports.stream().collect(joining(":"))));
            }
            if (addOpens.size() > 0) {
                result.add(JavaCore.newClasspathAttribute(ADD_OPENS, addOpens.stream().collect(joining(":"))));
            }
        }
        return result.toArray(new IClasspathAttribute[result.size()]);
    }

    /**
     * @return the {@link BazelProjectFileSystemMapper} (only set during
     *         {@link #provisionProjectsForSelectedTargets(Collection, BazelWorkspace, IProgressMonitor)})
     */
    protected BazelProjectFileSystemMapper getFileSystemMapper() {
        return requireNonNull(
            fileSystemMapper,
            "file system mapper not initialized, check code flow/implementation (likely a bug)");
    }

    private JvmConfigurator getJvmConfigurator() {
        if (jvmConfigurator == null) {
            jvmConfigurator = new JvmConfigurator();
        }
        return jvmConfigurator;
    }

    private String getOptionValue(String opt, String key) {
        var value = opt.substring(key.length()).trim();
        if (value.startsWith("=")) {
            value = value.substring(1).trim();
        }
        return value;
    }

    private IEclipsePreferences getPreferences(IProject project) {
        if (project != null) {
            return (IEclipsePreferences) Platform.getPreferencesService()
                    .getRootNode()
                    .node(ProjectScope.SCOPE)
                    .node(project.getName());
        }

        return (IEclipsePreferences) Platform.getPreferencesService().getRootNode().node(InstanceScope.SCOPE);
    }

    private boolean isTestTarget(BazelTarget bazelTarget) throws CoreException {
        return bazelTarget.getRuleClass().contains("test");
    }

    /**
     * Creates Eclipse virtual files/folders for sources collected in the {@link JavaProjectInfo}.
     *
     * @param project
     * @param javaInfo
     * @param progress
     * @throws CoreException
     */
    protected void linkSourcesIntoProject(BazelProject project, JavaProjectInfo javaInfo, IProgressMonitor progress)
            throws CoreException {
        var monitor = SubMonitor.convert(progress, 100);
        try {
            var sourceInfo = javaInfo.getSourceInfo();

            if (sourceInfo.hasSourceFilesWithoutCommonRoot()) {
                // create the "srcs" folder
                var virtualSourceFolder = getFileSystemMapper().getVirtualSourceFolder(project);
                createFolderAndParents(virtualSourceFolder, monitor.split(1));
                // build emulated Java package structure and link files
                var files = sourceInfo.getSourceFilesWithoutCommonRoot();
                Set<IFile> linkedFiles = new HashSet<>();
                for (JavaSourceEntry fileEntry : files) {
                    // peek at Java package to find proper "root"
                    var packagePath = fileEntry.getDetectedPackagePath();
                    var packageFolder = virtualSourceFolder.getFolder(packagePath);
                    if (!packageFolder.exists()) {
                        createFolderAndParents(packageFolder, monitor.split(1));
                    }

                    // create link to file
                    var file = packageFolder.getFile(fileEntry.getPath().lastSegment());
                    file.createLink(fileEntry.getLocation(), IResource.REPLACE, monitor.split(1));

                    // remember for cleanup
                    linkedFiles.add(file);
                }

                // remove all files not created as part of this loop
                deleteAllFilesNotInAllowList(virtualSourceFolder, linkedFiles, monitor.split(1));
            }

            if (sourceInfo.hasSourceDirectories()) {
                var directories = sourceInfo.getSourceDirectories();
                NEXT_FOLDER: for (IPath dir : directories) {
                    IFolder sourceFolder;
                    if (dir.isEmpty()) {
                        // special case ... source is directly within the project
                        // this is usually the case when the Bazel package is a Java package
                        // in this case we need to link (emulate) its package structure
                        // however, we can only support this properly if this is the only folder
                        if (directories.size() > 1) {
                            createBuildPathProblem(
                                project,
                                Status.error(
                                    "Impossible to support project: found multiple source directories which seems to be nested! Please consider restructuring the targets."));
                            continue NEXT_FOLDER;
                        }
                        // and there aren't any other source files to be linked
                        if (sourceInfo.hasSourceFilesWithoutCommonRoot()) {
                            createBuildPathProblem(
                                project,
                                Status.error(
                                    "Impossible to support project: found mix of source files without common root and empty package fragment root! Please consider restructuring the targets."));
                            continue NEXT_FOLDER;
                        }
                        // check this maps to a single Java package
                        var detectedJavaPackagesForSourceDirectory =
                                sourceInfo.getDetectedJavaPackagesForSourceDirectory(dir);
                        var packagePath = findCommonParentPackagePrefix(detectedJavaPackagesForSourceDirectory);
                        if (packagePath == null) {
                            createBuildPathProblem(
                                project,
                                Status.error(
                                    format(
                                        "Impossible to support project: an empty package fragment root must map to one Java package (got '%s')! Please consider restructuring the targets.",
                                        detectedJavaPackagesForSourceDirectory.isEmpty() ? "none"
                                                : detectedJavaPackagesForSourceDirectory.stream()
                                                        .map(IPath::toString)
                                                        .collect(joining(", ")))));
                            continue NEXT_FOLDER;
                        }

                        // create the "srcs" folder
                        var virtualSourceFolder = getFileSystemMapper().getVirtualSourceFolder(project);
                        if (virtualSourceFolder.exists() && virtualSourceFolder.isLinked()) {
                            // delete it to ensure we start fresh
                            virtualSourceFolder.delete(true, monitor.split(1));
                        }
                        createFolderAndParents(virtualSourceFolder, monitor.split(1));

                        // build emulated Java package structure and link the directory
                        var packageFolder = virtualSourceFolder.getFolder(packagePath);
                        if (!packageFolder.getParent().exists()) {
                            createFolderAndParents(packageFolder.getParent(), monitor.split(1));
                        }
                        if (packageFolder.exists() && !packageFolder.isLinked()) {
                            packageFolder.delete(true, monitor.split(1));
                        }
                        packageFolder.createLink(
                            javaInfo.getBazelPackage().getLocation(),
                            IResource.REPLACE,
                            monitor.split(1));

                        // remove all files not created as part of this loop
                        deleteAllFilesNotInFolderList(virtualSourceFolder, packageFolder, monitor.split(1));

                        // done
                        break;
                    }

                    // check for existing folder
                    sourceFolder = project.getProject().getFolder(dir);
                    if (sourceFolder.exists() && !sourceFolder.isLinked()) {
                        // check if there is any linked parent we can remove
                        var parent = sourceFolder.getParent();
                        while ((parent != null) && (parent.getType() != IResource.PROJECT)) {
                            if (parent.isLinked()) {
                                parent.delete(true, monitor.split(1));
                                break;
                            }
                            parent = parent.getParent();
                        }
                        if (sourceFolder.exists()) {
                            // TODO create problem marker
                            LOG.warn(
                                "Impossible to support project '{}' - found existing source directoy which cannot be deleted!",
                                project);
                            continue NEXT_FOLDER;
                        }
                    }

                    // ensure the parent exists
                    if (!sourceFolder.getParent().exists()) {
                        createFolderAndParents(sourceFolder.getParent(), monitor.split(1));
                    }

                    // create link to folder
                    sourceFolder.createLink(
                        javaInfo.getBazelPackage().getLocation().append(dir),
                        IResource.REPLACE,
                        monitor.split(1));
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
            var monitor = SubMonitor.convert(progress, "Provisioning projects", 3);

            // load all packages to be provisioned
            workspace.open(targets.stream().map(BazelTarget::getBazelPackage).distinct().toList());

            // ensure there is a mapper
            fileSystemMapper = new BazelProjectFileSystemMapper(workspace);

            // cleanup markers at workspace level
            workspace.getBazelProject()
                    .getProject()
                    .deleteMarkers(BUILDPATH_PROBLEM_MARKER, true, IResource.DEPTH_ZERO);

            // detect default Java level
            monitor.subTask("Detecting Java Toolchain");
            detectDefaultJavaToolchain(workspace);

            // create projects
            var result = doProvisionProjects(targets, monitor.split(1, SUPPRESS_NONE));

            // after provisioning we go over the projects a second time to initialize the classpaths
            doInitializeClasspaths(result, workspace, monitor.split(2, SUPPRESS_NONE));

            // done
            return result;
        } finally {
            progress.done();
        }
    }

    private void setLineSeparator(IEclipsePreferences projectPreferences, String value) throws CoreException {
        try {
            projectPreferences.node(PI_RUNTIME).put(PREF_LINE_SEPARATOR, value);
            projectPreferences.flush();
        } catch (BackingStoreException e) {
            throw new CoreException(Status.error("Error saving project setting.", e));
        }
    }

}
