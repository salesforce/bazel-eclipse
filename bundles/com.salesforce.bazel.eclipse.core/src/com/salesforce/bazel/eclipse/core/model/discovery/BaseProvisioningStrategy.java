/*-
 *
 */
package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BUILDPATH_PROBLEM_MARKER;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.CLASSPATH_CONTAINER_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.CLASSPATH_CONTAINER_PROBLEM_MARKER;
import static com.salesforce.bazel.eclipse.core.model.discovery.EclipsePreferencesHelper.convertToPreferences;
import static com.salesforce.bazel.eclipse.core.model.discovery.JvmConfigurator.VM_TYPE_RUNTIME;
import static com.salesforce.bazel.eclipse.core.model.discovery.JvmConfigurator.VM_TYPE_TOOLCHAIN;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.core.runtime.Platform.PI_RUNTIME;
import static org.eclipse.core.runtime.Platform.PREF_LINE_SEPARATOR;
import static org.eclipse.jdt.core.IClasspathAttribute.ADD_EXPORTS;
import static org.eclipse.jdt.core.IClasspathAttribute.ADD_OPENS;
import static org.eclipse.jdt.core.IClasspathAttribute.MODULE;
import static org.eclipse.jdt.core.JavaCore.COMPILER_RELEASE;
import static org.eclipse.jdt.core.JavaCore.DISABLED;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
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
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.osgi.service.prefs.BackingStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.core.model.BazelElement;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelProjectFileSystemMapper;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.EntrySettings;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaArchiveInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaResourceInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSourceInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaSrcJarEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.LabelEntry;
import com.salesforce.bazel.eclipse.core.util.trace.TracingSubMonitor;
import com.salesforce.bazel.sdk.command.BazelCQueryWithStarlarkExpressionCommand;
import com.salesforce.bazel.sdk.command.BazelQueryForLabelsCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Base class for provisioning strategies, providing common base logic re-usable by multiple strategies.
 */
public abstract class BaseProvisioningStrategy implements TargetProvisioningStrategy {

    private static final String FILE_EXTENSION_DOT_PREFS = ".prefs";

    private static final String JRE_SYSTEM_LIBRARY = "jre_system_library";
    private static final String JRE_SYSTEM_LIBRARY_RUNTIME = "current_java_runtime";
    private static final String JRE_SYSTEM_LIBRARY_EE = "execution_environment";

    private static final String JAVAC_OPT_ADD_OPENS = "--add-opens";
    private static final String JAVAC_OPT_ADD_EXPORTS = "--add-exports";

    private static final IClasspathAttribute CLASSPATH_ATTRIBUTE_FOR_TEST =
            JavaCore.newClasspathAttribute(IClasspathAttribute.TEST, Boolean.TRUE.toString());
    private static final IClasspathAttribute CLASSPATH_ATTRIBUTE_IGNORE_OPTIONAL_PROBLEMS =
            JavaCore.newClasspathAttribute(IClasspathAttribute.IGNORE_OPTIONAL_PROBLEMS, Boolean.TRUE.toString());

    private static final IPath[] EXCLUDE_JAVA_SOURCE = {
            IPath.forPosix("**/*.java") };

    private static Logger LOG = LoggerFactory.getLogger(BaseProvisioningStrategy.class);

    /**
     * Creates a problem marker of type {@link BazelCoreSharedContstants#CLASSPATH_CONTAINER_PROBLEM_MARKER} for the
     * given status.
     *
     * @param project
     *            the project to create the marker at (must not be <code>null</code>)
     * @param status
     *            the status to create the marker for (must not be <code>null</code>)
     * @return the created marker (never <code>null</code>)
     * @throws CoreException
     */
    static IMarker createClasspathContainerProblem(BazelProject project, IStatus status) throws CoreException {
        return createMarker(project.getProject(), CLASSPATH_CONTAINER_PROBLEM_MARKER, status);
    }

    static IMarker createMarker(IResource resource, String type, IStatus status) throws CoreException {
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

        if (message.length() >= 21000) {
            // marker content is limited in length
            message = message.substring(0, 20997).concat("...");
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

    private BazelProjectFileSystemMapper fileSystemMapper;
    /**
     * Eclipse VM representing the currecurrent_java_toolchain
     */
    protected IVMInstall javaToolchainVm;

    protected String javaToolchainSourceVersion;

    protected String javaToolchainTargetVersion;

    /**
     * Eclipse VM representing current_java_runtime
     */
    protected IVMInstall javaRuntimeVm;

    private JvmConfigurator jvmConfigurator;

    private String jreSystemLibraryConfiguration;

    /**
     * Adds all relevant information from a {@link BazelTarget} to the {@link JavaProjectInfo}.
     * <p>
     * This method obtains information from common <code>java_*</code> rule attributes.
     * </p>
     *
     * @param target
     * @throws CoreException
     */
    private void addInfoFromTarget(JavaProjectInfo javaInfo, BazelTarget bazelTarget) throws CoreException {
        var isTestTarget = isTestTarget(bazelTarget) || hasTestSources(bazelTarget);

        var attributes = bazelTarget.getRuleAttributes();

        var testonly = attributes.getBoolean("testonly");
        isTestTarget = isTestTarget || ((testonly != null) && testonly.booleanValue());

        var nowarn = false;
        var javacOpts = attributes.getStringList("javacopts");
        if (javacOpts != null) {
            for (String javacOpt : javacOpts) {
                javaInfo.addJavacOpt(javacOpt);
                if ("-nowarn".equals(javacOpt)) {
                    nowarn = true;
                }
            }
        }
        var settings = nowarn ? new EntrySettings(nowarn) : EntrySettings.DEFAULT_SETTINGS;

        var srcs = attributes.getStringList("srcs");
        if (srcs != null) {
            for (String src : srcs) {
                if (isTestTarget) {
                    javaInfo.addTestSrc(src, settings);
                } else {
                    javaInfo.addSrc(src, settings);
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

        var jars = attributes.getStringList("jars");
        if (jars != null) {
            var srcJar = attributes.getString("srcjar");
            for (String jar : jars) {
                // java_import is generally used to make classes and resources available on the classpath
                // lets check if we can translate this to resources in the same Bazel package
                var jarTarget = resolveToTargetInSamePackage(bazelTarget, jar);
                if (jarTarget != null) {
                    addResourcesFromRulesPkgRules(javaInfo, jarTarget, isTestTarget);
                } else if (isTestTarget) {
                    javaInfo.addTestJar(jar, srcJar);
                } else {
                    javaInfo.addJar(jar, srcJar);
                }
            }
        }
    }

    private void addJars(BazelProject project, List<IClasspathEntry> rawClasspath, JavaArchiveInfo jarInfo,
            boolean useTestsClasspath) {
        if (!jarInfo.hasJars()) {
            return;
        }

        var projectRoot = project.getProject().getFullPath();
        var classpathAttributes = useTestsClasspath ? new IClasspathAttribute[] {
                CLASSPATH_ATTRIBUTE_FOR_TEST } : new IClasspathAttribute[] {};

        var jarsAndSrcJar = jarInfo.getJars();
        for (Entry<IPath, IPath> entry : jarsAndSrcJar.entrySet()) {
            var fullPath = projectRoot.append(entry.getKey());
            var srcAttachmentPath = entry.getValue() != null ? projectRoot.append(entry.getValue()) : null;

            rawClasspath.add(
                JavaCore.newLibraryEntry(
                    fullPath,
                    srcAttachmentPath,
                    null,
                    null,
                    classpathAttributes,
                    true /* isExported */));
        }
    }

    private void addJavaContainerEntryAndConfigureJavaCompileSettings(List<IClasspathEntry> rawClasspath,
            IJavaProject javaProject, IClasspathAttribute[] extraAttributesForJdk) {
        var useEE =
                (jreSystemLibraryConfiguration == null) || JRE_SYSTEM_LIBRARY_EE.equals(jreSystemLibraryConfiguration);
        var executionEnvironmentId = getJvmConfigurator().getExecutionEnvironmentId(javaToolchainTargetVersion);
        if (useEE && (executionEnvironmentId != null)) {
            // prefer setting EE based JDK for compilation
            rawClasspath.add(
                getJvmConfigurator().getJreClasspathContainerForExecutionEnvironment(
                    executionEnvironmentId,
                    extraAttributesForJdk));
        } else if (JRE_SYSTEM_LIBRARY_RUNTIME.equals(jreSystemLibraryConfiguration) && (javaRuntimeVm != null)) {
            // Bazel 7 changed the Java compilation.
            // The runtime JVM is now responsible for the bootclasspath as well as the compilation java home (--system)
            // Of course, this is only considered when --release is not used.
            // We don't support --release yet.
            // But we prefer the runtime VM now because this indicates compile issues earlier.
            rawClasspath.add(
                JavaCore.newContainerEntry(
                    JavaRuntime.newJREContainerPath(javaRuntimeVm),
                    null /* no access rules */,
                    extraAttributesForJdk,
                    false /* not exported */));
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

        // tweak to current JVMconfiguration
        getJvmConfigurator()
                .applyJavaProjectOptions(javaProject, javaToolchainSourceVersion, javaToolchainTargetVersion, null);
    }

    private void addResourceFolders(BazelProject project, List<IClasspathEntry> rawClasspath,
            JavaResourceInfo resourceInfo, boolean useTestsClasspath) {
        var virtualResourceFolder = useTestsClasspath ? getFileSystemMapper().getVirtualResourceFolderForTests(project)
                : getFileSystemMapper().getVirtualResourceFolder(project);
        var outputLocation = useTestsClasspath ? getFileSystemMapper().getOutputFolderForTests(project).getFullPath()
                : getFileSystemMapper().getOutputFolder(project).getFullPath();
        var classpathAttributes = useTestsClasspath ? new IClasspathAttribute[] {
                CLASSPATH_ATTRIBUTE_FOR_TEST } : new IClasspathAttribute[] {};
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

    /**
     * Attempts to resolve a jar import into resource to be added to {@link JavaProjectInfo}.
     *
     * @param javaInfo
     *            the {@link JavaProjectInfo} to add resources to
     * @param bazelTarget
     *            the bazel target
     * @param archiveFileOrLabel
     *            the jar import (eg., from <code>java_import</code>)
     * @param isTestTarget
     *            whether or not the resources should be added as test resources
     * @throws CoreException
     */
    private void addResourcesFromRulesPkgRules(JavaProjectInfo javaInfo, BazelTarget rulesPkgTarget,
            boolean isTestTarget) throws CoreException {
        var rulesPkgRuleClass = rulesPkgTarget.getRuleClass();
        if (!rulesPkgRuleClass.startsWith("pkg_")) {
            LOG.debug("Unsupport rule '{}' for target '{}'", rulesPkgRuleClass, rulesPkgTarget.getLabel());
            return; // we don't support this
        }

        // inspect srcs
        var srcs = rulesPkgTarget.getRuleAttributes().getStringList("srcs");
        if (srcs != null) {
            // the strip_prefix in rules_pkg is relative to the package, need to make it absolute
            var resourceStripPrefix = rulesPkgTarget.getRuleAttributes().getString("strip_prefix");
            if ((resourceStripPrefix != null) && !resourceStripPrefix.isEmpty()) {
                resourceStripPrefix = rulesPkgTarget.getBazelPackage()
                        .getWorkspaceRelativePath()
                        .append(resourceStripPrefix)
                        .toString();
            }
            for (String src : srcs) {
                var srcTarget = resolveToTargetInSamePackage(rulesPkgTarget, src);
                if (srcTarget != null) {
                    // recurse into target
                    addResourcesFromRulesPkgRules(javaInfo, srcTarget, isTestTarget);
                } else {
                    var fileResource = resolveToFileInSamePackage(rulesPkgTarget, src);
                    if (fileResource != null) {
                        if (isTestTarget) {
                            javaInfo.addTestResource(fileResource, resourceStripPrefix);
                        } else {
                            javaInfo.addResource(fileResource, resourceStripPrefix);
                        }
                    }
                }
            }
        }
    }

    private void addSourceFolders(BazelProject project, List<IClasspathEntry> rawClasspath,
            JavaSourceInfo javaSourceInfo, boolean useTestsClasspath) throws CoreException {
        var virtualSourceFolder = useTestsClasspath ? getFileSystemMapper().getVirtualSourceFolderForTests(project)
                : getFileSystemMapper().getVirtualSourceFolder(project);
        var generatedSourceFolder = useTestsClasspath ? getFileSystemMapper().getGeneratedSourcesFolderForTests(project)
                : getFileSystemMapper().getGeneratedSourcesFolder(project);
        var outputLocation = useTestsClasspath ? getFileSystemMapper().getOutputFolderForTests(project).getFullPath()
                : getFileSystemMapper().getOutputFolder(project).getFullPath();
        var classpathAttributes = useTestsClasspath ? new IClasspathAttribute[] {
                CLASSPATH_ATTRIBUTE_FOR_TEST } : new IClasspathAttribute[] {};
        var classpathAttributesForIgnoringOptionalCompileProblems = useTestsClasspath ? new IClasspathAttribute[] {
                CLASSPATH_ATTRIBUTE_FOR_TEST,
                CLASSPATH_ATTRIBUTE_IGNORE_OPTIONAL_PROBLEMS }
                : new IClasspathAttribute[] {
                        CLASSPATH_ATTRIBUTE_IGNORE_OPTIONAL_PROBLEMS };
        if (javaSourceInfo.hasSourceFilesWithoutCommonRoot()) {
            // add the virtual folder for resources
            createFolderAndParents(virtualSourceFolder, new NullProgressMonitor());
            rawClasspath.add(
                JavaCore.newSourceEntry(
                    virtualSourceFolder.getFullPath(),
                    null /* include all */,
                    null /* exclude nothing */,
                    outputLocation,
                    javaSourceInfo.shouldDisableOptionalCompileProblemsForSourceFilesWithoutCommonRoot()
                            ? classpathAttributesForIgnoringOptionalCompileProblems : classpathAttributes));
        }
        if (javaSourceInfo.hasSourceDirectories()) {
            for (IPath dir : javaSourceInfo.getSourceDirectories()) {
                // check for srcjar
                if (dir.isAbsolute()) {
                    // double check
                    if (!javaSourceInfo.matchAllSourceDirectoryEntries(dir, JavaSrcJarEntry.class::isInstance)) {
                        throw new IllegalStateException(
                                format("programming error: found unsupported content for source directory '%s'", dir));
                    }

                    var srcjarFolder = generatedSourceFolder.getFolder(dir.lastSegment());
                    if (!srcjarFolder.exists()) {
                        createBuildPathProblem(
                            project,
                            Status.error(
                                format(
                                    "Folder '%s' does not exist. However, it's required for the project classpath to resolve properly. Please investigat!",
                                    srcjarFolder)));
                    } else {
                        rawClasspath.add(
                            JavaCore.newSourceEntry(
                                srcjarFolder.getFullPath(),
                                null,
                                null,
                                outputLocation,
                                classpathAttributesForIgnoringOptionalCompileProblems));
                    }

                    continue;
                }

                // when the directory is empty, the virtual "srcs" container must be used
                // this logic here requires proper linking support in linkSourcesIntoProject method
                var sourceFolder = dir.isEmpty() ? virtualSourceFolder : project.getProject().getFolder(dir);
                var inclusionPatterns = javaSourceInfo.getInclusionPatternsForSourceDirectory(dir);
                var exclusionPatterns = javaSourceInfo.getExclutionPatternsForSourceDirectory(dir);
                var existingEntry =
                        rawClasspath.stream().anyMatch(entry -> entry.getPath().equals(sourceFolder.getFullPath()));
                var isNested = rawClasspath.stream()
                        .anyMatch(
                            entry -> entry.getPath().isPrefixOf(sourceFolder.getFullPath())
                                    || sourceFolder.getFullPath().isPrefixOf(entry.getPath()));
                if (existingEntry) {
                    if (useTestsClasspath) {
                        createBuildPathProblem(
                            project,
                            Status.error(
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
                } else if (isNested) {
                    createBuildPathProblem(
                        project,
                        Status.error(
                            format(
                                "Folder '%s' is nested within an existing folder on the classpath. This is an unexpected situation. Please consider modifying the project setup! Don't hesitate and reach out for help.",
                                sourceFolder)));
                } else {
                    rawClasspath.add(
                        JavaCore.newSourceEntry(
                            sourceFolder.getFullPath(),
                            inclusionPatterns,
                            exclusionPatterns,
                            outputLocation,
                            javaSourceInfo.shouldDisableOptionalCompileProblemsForSourceDirectory(dir)
                                    ? classpathAttributesForIgnoringOptionalCompileProblems : classpathAttributes));
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
        var recommendations = getProjectRecommendations(javaInfo, monitor);

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
        deleteBuildPathProblems(project);

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
     * Calculates the java_library and java_imports for the provided targets, limited by the depth
     *
     * @param workspace
     *            the bazel workspace
     * @param targetsToBuild
     *            a list of targets as part of the build to query their dependencies
     * @param dependencyDepth
     *            the depth in the dependency graph to traverse and include in the result
     * @return a set of java_library and java_imports, or null, if partial classpath is disabled
     * @throws CoreException
     */
    protected final Set<BazelLabel> calculateWorkspaceDependencies(BazelWorkspace workspace,
            List<BazelLabel> targetsToBuild) throws CoreException {
        var dependencyDepth = workspace.getBazelProjectView().importDepth();
        if (dependencyDepth < 0) {
            return null;
        }
        if (dependencyDepth == 0) {
            return Collections.emptySet();
        }
        var targetLabels = targetsToBuild.stream().map(BazelLabel::toString).collect(joining(" + "));
        return workspace.getCommandExecutor()
                .runQueryWithoutLock(
                    new BazelQueryForLabelsCommand(
                            workspace.getLocation().toPath(),
                            format(
                                "kind(java_library, deps(%s, %d)) + kind(java_import, deps(%s, %d))",
                                targetLabels,
                                dependencyDepth,
                                targetLabels,
                                dependencyDepth),
                            true,
                            format("Querying for depdendencies for projects: %s", targetLabels)))
                .stream()
                .map(BazelLabel::new)
                .collect(toSet());
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

    private void configureAnnotationProcessors(IJavaProject javaProject, Collection<LabelEntry> pluginDeps) {
        //        var factoryPath = AptConfig.getDefaultFactoryPath(null);
        //
        //        for (LabelEntry pluginDep : pluginDeps) {
        //
        //        }
        // TODO - we need to resolve the label to outputs, but we may not have aspects here?
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
        var monitor = SubMonitor.convert(progress, "Setting classpath", 60);

        List<IClasspathEntry> rawClasspath = new ArrayList<>();

        addSourceFolders(project, rawClasspath, javaInfo.getSourceInfo(), false /* useTestsClasspath */);
        addResourceFolders(project, rawClasspath, javaInfo.getResourceInfo(), false /* useTestsClasspath */);

        addSourceFolders(project, rawClasspath, javaInfo.getTestSourceInfo(), true /* useTestsClasspath */);
        addResourceFolders(project, rawClasspath, javaInfo.getTestResourceInfo(), true /* useTestsClasspath*/);

        addJars(project, rawClasspath, javaInfo.getJarInfo(), false /* useTestsClasspath */);
        addJars(project, rawClasspath, javaInfo.getTestJarInfo(), true /* useTestsClasspath */);

        rawClasspath.add(JavaCore.newContainerEntry(new Path(CLASSPATH_CONTAINER_ID)));

        var javaProject = JavaCore.create(project.getProject());

        // apply settings configured in project view
        copyProjectSettings(project.getProject(), project.getBazelWorkspace());

        // configure JDK
        var extraAttributesForJdk = getExtraJvmAttributes(javaInfo);
        addJavaContainerEntryAndConfigureJavaCompileSettings(rawClasspath, javaProject, extraAttributesForJdk);

        // if the classpath has no source folder Eclipse will default to the whole project
        // this is not good for us because this could cause duplication of an entire hierarchy
        // we therefore ensure there is a default folder
        if (!rawClasspath.stream().anyMatch(e -> e.getEntryKind() == IClasspathEntry.CPE_SOURCE)) {
            // add the virtual folder for resources
            var virtualSourceFolder = getFileSystemMapper().getVirtualSourceFolder(project);
            createFolderAndParents(virtualSourceFolder, monitor.slice(20));
            rawClasspath.add(
                JavaCore.newSourceEntry(
                    virtualSourceFolder.getFullPath(),
                    null /* include all */,
                    null /* exclude nothing */,
                    getFileSystemMapper().getOutputFolder(project).getFullPath(),
                    null /* nothing */));
        }

        try {
            javaProject.setRawClasspath(
                rawClasspath.toArray(new IClasspathEntry[rawClasspath.size()]),
                true,
                monitor.slice(20));
            javaProject
                    .setOutputLocation(getFileSystemMapper().getOutputFolder(project).getFullPath(), monitor.slice(20));
        } catch (JavaModelException e) {
            // enrich error message with project name
            throw new CoreException(
                    Status.error(
                        format(
                            "Unable to configure raw classpath for project '%s': %s",
                            javaProject.getElementName(),
                            e.getMessage()),
                        e));
        }

        // if we have add-opens or add-export we need to turn release flag off
        // (https://stackoverflow.com/questions/45370178/exporting-a-package-from-system-module-is-not-allowed-with-release)
        if (Stream.of(extraAttributesForJdk)
                .anyMatch(a -> a.getName().equals(ADD_OPENS) || a.getName().equals(ADD_EXPORTS))) {
            javaProject.setOption(COMPILER_RELEASE, DISABLED);
        }

        // last (but not least) apply annotation processors
        configureAnnotationProcessors(javaProject, javaInfo.getPluginDeps());
    }

    /**
     * Configures the raw classpath of the workspace project according to the needs of the strategy.
     *
     * @param project
     *            the workspace project
     * @param monitor
     * @throws JavaModelException
     */
    protected void configureRawClasspathOfWorkspaceProject(IProject project, IProgressMonitor monitor)
            throws JavaModelException {
        var javaProject = JavaCore.create(project);

        // configure JDK and update project settings
        List<IClasspathEntry> rawClasspath = new ArrayList<>();
        addJavaContainerEntryAndConfigureJavaCompileSettings(rawClasspath, javaProject, new IClasspathAttribute[] {});

        // container for all external libraries
        rawClasspath.add(JavaCore.newContainerEntry(new Path(CLASSPATH_CONTAINER_ID)));

        // apply to project
        javaProject.setRawClasspath(rawClasspath.toArray(new IClasspathEntry[rawClasspath.size()]), true, monitor);
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

        var targetPreferences = getPreferences(target);

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
            IProgressMonitor monitor) throws CoreException {
        monitor.beginTask("Creating Project " + projectName, 10);
        try {
            // locate existing project by its location (never use the name)
            var project = findProjectForLocation(projectLocation);

            // open existing
            if ((project != null) && !project.isOpen()) {
                try {
                    project.open(monitor.slice(1));
                } catch (CoreException e) {
                    LOG.warn("Unable to open existing project '{}'. Deleting and re-creating the project.", project, e);
                    project.delete(IResource.NEVER_DELETE_PROJECT_CONTENT, monitor.slice(1));
                    project = null;
                }
            }

            // check for name collection
            if ((project == null) || !project.exists()) {
                project = getEclipseWorkspaceRoot().getProject(projectName);
                if (project.exists()) {
                    LOG.warn(
                        "Found existing project with name'{}' at different location. Deleting and re-creating the project.",
                        project);
                    project.delete(IResource.NEVER_DELETE_PROJECT_CONTENT, monitor.slice(1));
                }

                // create new project
                var projectDescription = getEclipseWorkspace().newProjectDescription(projectName);
                projectDescription.setLocation(projectLocation);
                projectDescription.setComment(format("Bazel project representing '%s'", owner.getLabel()));
                project.create(projectDescription, monitor.slice(1));

                // ensure project is open (creating a project which failed opening previously will create a closed project)
                if (!project.isOpen()) {
                    project.open(monitor.slice(1));
                }
            } else {
                // open existing
                if (!project.isOpen()) {
                    project.open(monitor.slice(1));
                }

                // fix name
                if (!projectName.equals(project.getName())) {
                    var projectDescription = project.getDescription();
                    projectDescription.setName(projectName);
                    projectDescription.setComment(format("Bazel project representing '%s'", owner.getLabel()));
                    project.move(projectDescription, true, monitor.slice(1));
                }
            }

            // set natures separately in order to ensure they are configured properly
            var projectDescription = project.getDescription();
            projectDescription.setNatureIds(
                new String[] {
                        JavaCore.NATURE_ID,
                        BAZEL_NATURE_ID });
            project.setDescription(projectDescription, monitor.slice(1));

            // set properties
            project.setPersistentProperty(
                BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT,
                getFileSystemMapper().getBazelWorkspace().getLocation().toString());
            project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_OWNER, owner.getLabel().getLabelPath());

            // set encoding to UTF-8
            project.setDefaultCharset(StandardCharsets.UTF_8.name(), monitor.slice(1));

            // set line separator to posix
            setLineSeparator(getPreferences(project), "\n");

            return project;
        } finally {
            monitor.done();
        }

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
     * Convenience method to delete all markers of type {@link BazelCoreSharedContstants#BUILDPATH_PROBLEM_MARKER} from
     * the Bazel project
     *
     * @param project
     *            the project to create the marker at (must not be <code>null</code>)
     * @throws CoreException
     */
    protected void deleteBuildPathProblems(BazelProject project) throws CoreException {
        project.getProject().deleteMarkers(BUILDPATH_PROBLEM_MARKER, true, IResource.DEPTH_ZERO);
    }

    /**
     * Convenience method to delete all markers of type
     * {@link BazelCoreSharedContstants#CLASSPATH_CONTAINER_PROBLEM_MARKER} from the Bazel project
     *
     * @param project
     *            the project to create the marker at (must not be <code>null</code>)
     * @throws CoreException
     */
    protected void deleteClasspathContainerProblems(BazelProject project) throws CoreException {
        project.getProject().deleteMarkers(CLASSPATH_CONTAINER_PROBLEM_MARKER, true, IResource.DEPTH_ZERO);
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
                "@bazel_tools//tools/jdk:current_java_toolchain + @bazel_tools//tools/jdk:current_java_runtime",
                """
                        def format(target):
                            toolchain_infos = {k: v for k, v in providers(target).items() if k.endswith('JavaToolchainInfo')}
                            runtime_infos = {k: v for k, v in providers(target).items() if k.endswith('JavaRuntimeInfo')}

                            if len(toolchain_infos) == 1:
                                java_toolchain_info = toolchain_infos.values()[0]
                                return 'java_toolchain_info_source_version=' + java_toolchain_info.source_version + '\\njava_toolchain_info_target_version=' + java_toolchain_info.target_version + '\\njava_toolchain_info_java_home=' + java_toolchain_info.java_runtime.java_home

                            if len(runtime_infos) == 1:
                                java_runtime_info = runtime_infos.values()[0]
                                return 'java_runtime_info_java_home=' + java_runtime_info.java_home

                            fail("Unable to obtain JavaToolchainInfo or JavaRuntimeInfo.")
                            """,
                false,
                "Querying for Java toolchain information");
        var result = workspace.getCommandExecutor().runQueryWithoutLock(command).trim();
        try {
            var properties = new Properties();
            properties.load(new StringReader(result));

            javaToolchainSourceVersion = requireNonNull(
                properties.getProperty("java_toolchain_info_source_version"),
                "java_toolchain_info_source_version missing");
            javaToolchainTargetVersion = requireNonNull(
                properties.getProperty("java_toolchain_info_target_version"),
                "java_toolchain_info_target_version missing");
            var javaHome = requireNonNull(
                properties.getProperty("java_toolchain_info_java_home"),
                "java_toolchain_info_java_home missing");
            LOG.debug(
                "JavaToolchainInfo source_level: {}, target_level: {}, java_home: {}",
                javaToolchainSourceVersion,
                javaToolchainTargetVersion,
                javaHome);
            var javaRuntimeHome = requireNonNull(
                properties.getProperty("java_runtime_info_java_home"),
                "java_runtime_info_java_home missing");
            LOG.debug("JavaRuntimeInfo java_home: {}", javaRuntimeHome);

            // sanitize versions
            javaToolchainSourceVersion = sanitizeVersion(javaToolchainSourceVersion, "source level");
            javaToolchainTargetVersion = sanitizeVersion(javaToolchainTargetVersion, "target level");

            // resolve java home
            var resolvedJavaHomePath = resolveJavaHome(workspace, javaHome, "Java Toolchain");
            var resolvedJavaRuntimeHomePath = resolveJavaHome(workspace, javaRuntimeHome, "Java Runtime");

            javaToolchainVm =
                    getJvmConfigurator().configureVMInstall(resolvedJavaHomePath, workspace, VM_TYPE_TOOLCHAIN);
            javaRuntimeVm =
                    getJvmConfigurator().configureVMInstall(resolvedJavaRuntimeHomePath, workspace, VM_TYPE_RUNTIME);

            // remove old VMs
            getJvmConfigurator()
                    .deleteObsoleteVMInstallsKeepingOnlySpecified(workspace, javaToolchainVm, javaRuntimeVm);

            jreSystemLibraryConfiguration = workspace.getBazelProjectView()
                    .targetProvisioningSettings()
                    .getOrDefault(JRE_SYSTEM_LIBRARY, JRE_SYSTEM_LIBRARY_EE);
        } catch (NoSuchElementException | IOException e) {
            throw new CoreException(
                    Status.error(
                        format("Unable to detect Java information. Error parsing output of bazel cquery (%s)", result),
                        e));
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
    protected abstract List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets,
            TracingSubMonitor monitor) throws CoreException;

    private void ensureFolderLinksToTarget(IFolder folderWhichShouldBeALink, IPath linkTarget, SubMonitor monitor)
            throws CoreException {
        if (folderWhichShouldBeALink.exists() && !folderWhichShouldBeALink.isLinked()) {
            folderWhichShouldBeALink.delete(true, monitor.split(1));
        }
        folderWhichShouldBeALink.createLink(linkTarget, IResource.REPLACE, monitor.split(1));
    }

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

    private void garbageCollectFolderContent(IFolder folder, Set<IResource> membersToRetain, SubMonitor monitor)
            throws CoreException {
        if (!folder.exists()) {
            return;
        }

        for (IResource member : folder.members()) {
            if (!membersToRetain.contains(member)) {
                member.delete(IResource.NONE, monitor.split(1));
            }
        }

        if (folder.members().length == 0) {
            folder.delete(IResource.NONE, monitor.split(1));
        }
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

    /**
     * Calls and returns {@link JavaProjectInfo#analyzeProjectRecommendations(boolean, IProgressMonitor)} with
     * recommended defaults.
     * <p>
     * Subclasses may override to customize defaults
     * </p>
     *
     * @param javaInfo
     *            the {@link JavaProjectInfo}
     * @param monitor
     *            progress monitor
     * @return the recommendations returned by
     *         {@link JavaProjectInfo#analyzeProjectRecommendations(boolean, IProgressMonitor)}
     * @throws CoreException
     */
    protected IStatus getProjectRecommendations(JavaProjectInfo javaInfo, IProgressMonitor monitor)
            throws CoreException {
        return javaInfo.analyzeProjectRecommendations(true, monitor);
    }

    /**
     * Indicates if the target has sources matching any of the test_sources globs defined in the workspace's project
     * view.
     * <p>
     * If the target package location matches any of the globs this method will return <code>true</code>. Otherwise the
     * target rule attribute "<code>srcs</code>" is checked for matchinf files. Labels in referenced in
     * <code>srcs</code> will not be checked.
     * </p>
     *
     * @param bazelTarget
     *            the target to check
     * @return <code>true</code> if the target contains any test source, <code>false</code> otherwise
     * @throws CoreException
     */
    private boolean hasTestSources(BazelTarget bazelTarget) throws CoreException {
        var testSourcesMatcher = bazelTarget.getBazelWorkspace().getBazelProjectView().testSourcesGlobs();
        if (testSourcesMatcher.getGlobs().isEmpty()) {
            return false;
        }

        if (testSourcesMatcher.matches(bazelTarget.getBazelPackage().getWorkspaceRelativePath().toPath())) {
            return true;
        }

        var attributes = bazelTarget.getRuleAttributes();
        var srcs = attributes.getStringList("srcs");
        if (srcs != null) {
            for (String src : srcs) {
                if (src.contains(BazelLabel.BAZEL_COLON)) {
                    continue; // ignore labels
                }
                if (testSourcesMatcher.matches(java.nio.file.Path.of(src))) {
                    return true; // first match wins
                }
            }
        }
        return false;
    }

    /**
     * Indicates if a target uses a test rule.
     * <p>
     * Per Bazel style-guide, a test rule must end with <code>_test</code> or the target name must end with
     * <code>_test</code>, <code>_unittest</code>, <code>Test</code>, or <code>Tests</code>.
     * </p>
     *
     * @param bazelTarget
     *            the target to check
     * @return <code>true</code> if this can be considered a test target
     * @throws CoreException
     * @see https://bazel.build/build/style-guide
     */
    private boolean isTestTarget(BazelTarget bazelTarget) throws CoreException {
        var targetName = bazelTarget.getTargetName();
        var ruleClass = bazelTarget.getRuleClass();
        return ruleClass.endsWith("_test") // java_test
                || targetName.endsWith("_test") // Bazel style guide
                || targetName.endsWith("_unittest") // Bazel style guide
                || targetName.endsWith("Test") // Bazel style guide
                || targetName.endsWith("Tests") // Bazel style guide
                || targetName.endsWith("-test-lib"); // java_test_suite shared lib from @contrib_rules_jvm
    }

    private void linkFile(IFile file, IPath target, SubMonitor monitor) throws CoreException {
        if (file.exists() && !file.isLinked()) {
            file.delete(true, monitor.newChild(1));
        }
        file.createLink(target, IResource.REPLACE, monitor.newChild(1));
    }

    private void linkGeneratedSourceDirectories(JavaSourceInfo sourceInfo, IFolder generatedSourcesFolder,
            SubMonitor monitor) throws CoreException {
        if (!sourceInfo.hasSourceDirectories()) {
            return;
        }

        var directories = sourceInfo.getSourceDirectories();

        // capture created srcjar folders for GC later
        var generatedSourcesMembers = new HashSet<IResource>();

        // check each directory
        for (IPath dir : directories) {
            // ignore non-srcjars
            if (!dir.isAbsolute()) {
                continue;
            }

            // double check
            if (!sourceInfo.matchAllSourceDirectoryEntries(dir, JavaSrcJarEntry.class::isInstance)) {
                throw new IllegalStateException(
                        format("programming error: found unsupported content for source directory '%s'", dir));
            }

            if (!generatedSourcesFolder.exists()) {
                createFolderAndParents(generatedSourcesFolder, monitor.split(1));
            }

            var srcjarFolder = generatedSourcesFolder.getFolder(dir.lastSegment());
            ensureFolderLinksToTarget(srcjarFolder, dir, monitor);
            generatedSourcesMembers.add(srcjarFolder);
        }

        // cleanup any old generated srcjar folders
        garbageCollectFolderContent(generatedSourcesFolder, generatedSourcesMembers, monitor);
    }

    /**
     * Creates Eclipse virtual folders for generated sources collected in the {@link JavaSourceInfo}.
     *
     * @param project
     * @param sourceInfo
     * @param progress
     * @throws CoreException
     */
    protected void linkGeneratedSourcesIntoProject(BazelProject project, JavaProjectInfo javaInfo,
            IProgressMonitor progress) throws CoreException {
        var monitor = SubMonitor.convert(progress, 100);
        try {
            linkGeneratedSourceDirectories(
                javaInfo.getSourceInfo(),
                getFileSystemMapper().getGeneratedSourcesFolder(project),
                monitor);
            linkGeneratedSourceDirectories(
                javaInfo.getTestSourceInfo(),
                getFileSystemMapper().getGeneratedSourcesFolderForTests(project),
                monitor);
        } finally {
            progress.done();
        }
    }

    /**
     * Creates Eclipse virtual files/folders for sources collected in the {@link JavaSourceInfo}.
     *
     * @param project
     * @param sourceInfo
     * @param progress
     * @throws CoreException
     */
    protected void linkJarsIntoProject(BazelProject project, JavaProjectInfo javaInfo, IProgressMonitor progress)
            throws CoreException {
        var monitor = SubMonitor.convert(progress, 100);
        try {
            if (javaInfo.getJarInfo().hasJars()) {
                linkJarsIntoProject(project, javaInfo, javaInfo.getJarInfo(), monitor);
            }
            if (javaInfo.getTestJarInfo().hasJars()) {
                linkJarsIntoProject(project, javaInfo, javaInfo.getTestJarInfo(), monitor);
            }
        } finally {
            progress.done();
        }
    }

    private void linkJarsIntoProject(BazelProject project, JavaProjectInfo javaInfo, JavaArchiveInfo jarInfo,
            SubMonitor monitor) throws CoreException {
        for (Entry<IPath, IPath> entry : jarInfo.getJars().entrySet()) {
            var jarFileLocation = javaInfo.getBazelPackage().getLocation().append(entry.getKey());
            var jarFile = project.getProject().getFile(jarFileLocation.lastSegment());
            linkFile(jarFile, jarFileLocation, monitor.split(1));
            if (entry.getValue() != null) {
                var srcjarFileLocation = javaInfo.getBazelPackage().getLocation().append(entry.getValue());
                var srcjarFile = project.getProject().getFile(jarFileLocation.lastSegment());
                linkFile(srcjarFile, srcjarFileLocation, monitor.split(1));
            }
        }
    }

    private void linkSourceDirectories(BazelProject project, JavaProjectInfo javaInfo, JavaSourceInfo sourceInfo,
            IFolder virtualSourceFolder, IFolder generatedSourcesFolder, SubMonitor monitor) throws CoreException {
        if (!sourceInfo.hasSourceDirectories()) {
            return;
        }

        var directories = sourceInfo.getSourceDirectories();

        // check each directory
        NEXT_FOLDER: for (IPath dir : directories) {
            // ignore srcjars
            if (dir.isAbsolute()) {
                continue;
            }

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
                var detectedJavaPackagesForSourceDirectory = sourceInfo.getDetectedJavaPackagesForSourceDirectory(dir);
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
                ensureFolderLinksToTarget(packageFolder, javaInfo.getBazelPackage().getLocation(), monitor);

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

    private void linkSourceFilesWithoutCommonRoot(JavaSourceInfo sourceInfo, IFolder virtualSourceFolder,
            SubMonitor monitor) throws CoreException {
        if (!sourceInfo.hasSourceFilesWithoutCommonRoot()) {
            return;
        }

        // create the "srcs" folder
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

    /**
     * Creates Eclipse virtual files/folders for sources collected in the {@link JavaSourceInfo}.
     *
     * @param project
     * @param sourceInfo
     * @param progress
     * @throws CoreException
     */
    protected void linkSourcesIntoProject(BazelProject project, JavaProjectInfo javaInfo, IProgressMonitor progress)
            throws CoreException {
        var monitor = SubMonitor.convert(progress, 100);
        try {
            linkSourceFilesWithoutCommonRoot(
                javaInfo.getSourceInfo(),
                getFileSystemMapper().getVirtualSourceFolder(project),
                monitor);
            linkSourceDirectories(
                project,
                javaInfo,
                javaInfo.getSourceInfo(),
                getFileSystemMapper().getVirtualSourceFolder(project),
                getFileSystemMapper().getGeneratedSourcesFolder(project),
                monitor);

            linkSourceFilesWithoutCommonRoot(
                javaInfo.getSourceInfo(),
                getFileSystemMapper().getVirtualSourceFolderForTests(project),
                monitor);
            linkSourceDirectories(
                project,
                javaInfo,
                javaInfo.getTestSourceInfo(),
                getFileSystemMapper().getVirtualSourceFolderForTests(project),
                getFileSystemMapper().getGeneratedSourcesFolderForTests(project),
                monitor);

            // ensure the BUILD file is linked
            var buildFileLocation = javaInfo.getBazelPackage().getBuildFileLocation();
            var buildFile = project.getProject().getFile(buildFileLocation.lastSegment());
            linkFile(buildFile, buildFileLocation, monitor);
        } finally {
            progress.done();
        }
    }

    @Override
    public List<BazelProject> provisionProjectsForSelectedTargets(Collection<BazelTarget> targets,
            BazelWorkspace workspace, IProgressMonitor progress) throws CoreException {
        try {
            var monitor = TracingSubMonitor.convert(progress, "Provisioning projects", 3);

            // load all packages to be provisioned
            workspace.open(targets.stream().map(BazelTarget::getBazelPackage).distinct().toList());

            // ensure there is a mapper
            fileSystemMapper = new BazelProjectFileSystemMapper(workspace);

            // cleanup markers at workspace level
            deleteBuildPathProblems(workspace.getBazelProject());

            // detect default Java level
            monitor.subTask("Detecting Java Toolchain");
            detectDefaultJavaToolchain(workspace);

            // configure the classpath of the workspace project
            configureRawClasspathOfWorkspaceProject(workspace.getBazelProject().getProject(), monitor.slice(1));

            // create projects
            return doProvisionProjects(targets, monitor);
        } finally {
            progress.done();
        }
    }

    private java.nio.file.Path resolveJavaHome(BazelWorkspace workspace, String javaHome, String description)
            throws CoreException {
        var resolvedJavaHomePath = java.nio.file.Path.of(javaHome);
        if (!resolvedJavaHomePath.isAbsolute()) {
            if (!javaHome.startsWith("external/")) {
                throw new CoreException(
                        Status.error(
                            format(
                                "Unable to resolved java_home of %s (%s) into something meaningful. Please report as reproducible bug!",
                                description,
                                javaHome)));
            }
            resolvedJavaHomePath = new BazelWorkspaceBlazeInfo(workspace).getOutputBase().resolve(javaHome);
        }
        return resolvedJavaHomePath;
    }

    private String resolveToFileInSamePackage(BazelTarget bazelTarget, String maybeLabel) {
        var label = Label.createIfValid(maybeLabel);
        if ((label != null) && label.blazePackage()
                .relativePath()
                .equals(bazelTarget.getBazelPackage().getWorkspaceRelativePath().toString())) {
            // remove package reference and treat as file
            return label.targetName().toString();
        }
        return null; // outside of package or not a valid label
    }

    /**
     * Attempts to resolve the given label to a target within the same package.
     *
     * @param bazelTarget
     *            the bazel target as reference
     * @param maybeLabel
     *            a potential label
     * @return the resolved label or <code>null</code>
     * @throws CoreException
     */
    private BazelTarget resolveToTargetInSamePackage(BazelTarget bazelTarget, String maybeLabel) throws CoreException {
        // test if this may be a target in this package
        var myPackagePath = bazelTarget.getBazelPackage().getLabel().toString();
        if (maybeLabel.startsWith(myPackagePath + BazelLabel.BAZEL_COLON)) {
            // drop the package name to identify a reference within package
            maybeLabel = maybeLabel.substring(myPackagePath.length() + 1);
        }

        // starts with : then it must be treated as label; drop to colon so we can attempt to resolve it
        if (maybeLabel.startsWith(BazelLabel.BAZEL_COLON)) {
            maybeLabel = maybeLabel.substring(1);
        }

        // starts with // or @ then it must be treated as an external label
        if (maybeLabel.startsWith(BazelLabel.BAZEL_ROOT_SLASHES)
                || maybeLabel.startsWith(BazelLabel.BAZEL_EXTERNALREPO_AT)) {
            return null; // we don't support this
        }

        var targetName = TargetName.createIfValid(maybeLabel);
        if (targetName == null) {
            // likely a file
            if (LOG.isDebugEnabled()) {
                LOG.debug("'{}' is not a valid target name.", maybeLabel);
            }
            return null;
        }
        var resolvedTarget = bazelTarget.getBazelPackage().getBazelTarget(targetName.toString());
        if (!resolvedTarget.exists()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("'{}' is not a target in package '{}'.", maybeLabel, myPackagePath);
            }
            return null; // should be a jar file
        }

        return resolvedTarget;
    }

    private String sanitizeVersion(String version, String description) throws CoreException {
        try {
            if (Integer.parseInt(version) < 9) {
                return "1." + version;
            }
            return version;
        } catch (NumberFormatException e) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Unable to detect Java Toolchain information. Error parsing %s (%s)",
                            description,
                            version),
                        e));
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
