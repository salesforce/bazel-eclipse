package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BUILDPATH_PROBLEM_MARKER;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.CLASSPATH_CONTAINER_ID;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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

import com.google.devtools.build.lib.view.proto.Deps;
import com.google.devtools.build.lib.view.proto.Deps.Dependency;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelProjectFileSystemMapper;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaInfo.FileEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;
import com.salesforce.bazel.sdk.command.BazelCQueryWithStarlarkExpressionCommand;

/**
 * Default implementation of {@link TargetProvisioningStrategy} which provisions a single project per supported target.
 *
 * @since 2.0
 */
public class ProjectPerTargetProvisioningStrategy implements TargetProvisioningStrategy {

    private static Logger LOG = LoggerFactory.getLogger(ProjectPerTargetProvisioningStrategy.class);

    public static final String STRATEGY_NAME = "project-per-target";

    private static boolean relevantDep(Deps.Dependency dep) {
        // we only want explicit or implicit deps that were actually resolved by the compiler, not ones
        // that are available for use in the same package
        return (dep.getKind() == Deps.Dependency.Kind.EXPLICIT) || (dep.getKind() == Deps.Dependency.Kind.IMPLICIT);
    }

    private static BlazeArtifact resolveJdepsOutput(ArtifactLocationDecoder decoder, TargetIdeInfo target) {
        var javaIdeInfo = target.getJavaIdeInfo();
        if ((javaIdeInfo == null) || (javaIdeInfo.getJdepsFile() == null)) {
            return null;
        }
        return decoder.resolveOutput(javaIdeInfo.getJdepsFile());
    }

    private volatile BazelProjectFileSystemMapper fileSystemMapper;

    private IVMInstall javaToolchainVm;

    private String javaToolchainSourceVersion;
    private String javaToolchainTargetVersion;

    /**
     * Collects base Java information for a given target.
     * <p>
     * This uses the target info from the model (as returned by <code>bazel query</code>) to discover source directories
     * and project level dependencies. This does not compute the classpath. Instead a classpath container is applied to
     * defer classpath computation when project provisioning is completed for a workspace.
     * </p>
     *
     * @param target
     *            the target to collect Java information for (must not be <code>null</code>)
     * @param project
     *            the provisioned Bazel project (must not be <code>null</code>)
     * @param monitor
     *            the progress monitor for checking cancellation (must not be <code>null</code>)
     * @return the collected Java info (never <code>null</code>)
     * @throws CoreException
     */
    protected JavaInfo collectJavaInfo(BazelTarget target, BazelProject project, IProgressMonitor monitor)
            throws CoreException {
        var javaInfo = new JavaInfo(target.getBazelPackage(), target.getBazelWorkspace());

        var attributes = target.getRuleAttributes();
        var srcs = attributes.getStringList("srcs");
        if (srcs != null) {
            for (String src : srcs) {
                LOG.debug("{} adding src: {}", target, src);
                javaInfo.addSrc(src);
            }
        }

        var deps = attributes.getStringList("deps");
        if (deps != null) {
            for (String dep : deps) {
                LOG.debug("{} adding dep: {}", target, dep);
                javaInfo.addDep(dep);
            }
        }

        var runtimeDeps = attributes.getStringList("runtime_deps");
        if (runtimeDeps != null) {
            for (String dep : runtimeDeps) {
                LOG.debug("{} adding runtime dep: {}", target, dep);
                javaInfo.addRuntimeDep(dep);
            }
        }

        // analyze for recommended project setup
        var recommendations = javaInfo.analyzeProjectRecommendations(monitor);

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} source directories: {}", target,
                javaInfo.hasSourceDirectories() ? javaInfo.getSourceDirectories() : "n/a");
            LOG.debug("{} source files without root: {}", target,
                javaInfo.hasSourceFilesWithoutCommonRoot() ? javaInfo.getSourceFilesWithoutCommonRoot() : "n/a");
        }

        // delete existing markers
        project.getProject().deleteMarkers(BUILDPATH_PROBLEM_MARKER, true, IResource.DEPTH_ZERO);

        // abort if canceled
        if (monitor.isCanceled()) {
            createMarker(target.getBazelWorkspace().getBazelProject().getProject(), BUILDPATH_PROBLEM_MARKER,
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

        return javaInfo;
    }

    @Override
    public List<ClasspathEntry> computeClasspath(BazelProject bazelProject, BazelClasspathScope scope,
            IProgressMonitor monitor) throws CoreException {

        var bazelWorkspace = bazelProject.getBazelWorkspace();
        var workspaceRoot = bazelWorkspace.getLocation().toFile().toPath();

        if (bazelProject.isWorkspaceProject()) {
            // FIXME: implement support for reading all jars from WORKSPACE
            //
            // For example:
            //   1. get list of all external repos
            //      > bazel query "//external:*"
            //   2. query for java rules for each external repo
            //      > bazel query "kind('java_.* rule', @evernal_repo_name//...)"
            //
            return List.of();
        }
        if (!bazelProject.isSingleTargetProject()) {
            throw new CoreException(Status.error(format(
                "Unable to compute classpath for project '%s'. Please check the setup. This is not a Bazel target project created by the project per target strategy.",
                bazelProject)));
        }
        var targets = List.of(bazelProject.getBazelTarget().getLabel());
        var onlyDirectDeps = bazelWorkspace.getBazelProjectView().deriveTargetsFromDirectories();
        var outputGroups = Set.of(OutputGroup.INFO, OutputGroup.RESOLVE);
        var languages = Set.of(LanguageClass.JAVA);
        var aspects = bazelWorkspace.getParent().getModelManager().getIntellijAspects();
        var command = new BazelBuildWithIntelliJAspectsCommand(workspaceRoot, targets, outputGroups, aspects, languages,
                onlyDirectDeps);

        var executionService = bazelWorkspace.getParent().getModelManager().getExecutionService();
        var result = executionService.executeWithWorkspaceLock(command, bazelWorkspace,
            List.of(bazelProject.getProject()), monitor);

        if (LOG.isDebugEnabled()) {
            result.getOutputGroupArtifacts(OutputGroup.RESOLVE::isPrefixOf)
                    .forEach(o -> LOG.debug("Resolve Output: {}", o));
            result.getOutputGroupArtifacts(OutputGroup.INFO::isPrefixOf).forEach(o -> LOG.debug("Info Output: {}", o));
        }

        ArtifactLocationDecoder decoder = new ArtifactLocationDecoderImpl(new BazelWorkspaceBlazeInfo(bazelWorkspace),
                new WorkspacePathResolverImpl(new WorkspaceRoot(workspaceRoot)));
        var outputArtifacts = result.getOutputGroupArtifacts(OutputGroup.INFO::isPrefixOf,
            IntellijAspects.ASPECT_OUTPUT_FILE_PREDICATE);
        for (OutputArtifact outputArtifact : outputArtifacts) {
            try {
                var info = TargetIdeInfo.fromProto(aspects.readAspectFile(outputArtifact));
                var javaIdeInfo = info.getJavaIdeInfo();
                if (javaIdeInfo != null) {
                    var jdepsFile = resolveJdepsOutput(decoder, info);
                    if (jdepsFile instanceof OutputArtifact) {
                        LOG.debug("parsing: {}", jdepsFile);
                        try (InputStream in = jdepsFile.getInputStream()) {
                            var dependencies = Deps.Dependencies.parseFrom(in);
                            if (dependencies != null) {
                                List<String> deps = dependencies.getDependencyList().stream()
                                        .filter(ProjectPerTargetProvisioningStrategy::relevantDep)
                                        .map(Dependency::getPath).collect(toList());
                                LOG.debug("deps: {}", deps);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new CoreException(Status
                        .error(format("Unable to compute classpath for project '%s'. Error reading aspect file '%s'.",
                            bazelProject, outputArtifact), e));
            }
        }

        // convert the logical entries into concrete Eclipse entries
        //        List<IClasspathEntry> entries = new ArrayList<>();
        //        for (JvmClasspathEntry entry : jcmClasspathData.jvmClasspathEntries) {
        //            if (entry.pathToJar != null) {
        //                var jarPath = getAbsoluteLocation(entry.pathToJar);
        //                if (jarPath != null) {
        //                    // srcJarPath must be relative to the workspace, by order of Eclipse
        //                    var srcJarPath = getAbsoluteLocation(entry.pathToSourceJar);
        //                    IPath srcJarRootPath = null;
        //                    entries.add(newLibraryEntry(jarPath, srcJarPath, srcJarRootPath, entry.isTestJar));
        //                }
        //            } else {
        //                entries.add(newProjectEntry(entry.bazelProject));
        //            }
        //        }

        return null;

    }

    private void configureRawClasspath(BazelTarget target, BazelProject project, JavaInfo javaInfo,
            IProgressMonitor progress) throws CoreException {
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

    private void detectDefaultJavaToolchain(BazelWorkspace workspace) throws CoreException {
        var command = new BazelCQueryWithStarlarkExpressionCommand(workspace.getLocation().toFile().toPath(),
                "@bazel_tools//tools/jdk:current_java_toolchain",
                "providers(target)['JavaToolchainInfo'].source_version + '::' + providers(target)['JavaToolchainInfo'].target_version + '::' + providers(target)['JavaToolchainInfo'].java_runtime.java_home",
                false);
        try {
            var result = workspace.getParent().getModelManager().getExecutionService()
                    .executeOutsideWorkspaceLockAsync(command, workspace).get();
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
                    throw new CoreException(Status.error(
                        format("Unable to detect Java Toolchain information. Error parsing source level (%s)",
                            javaToolchainSourceVersion),
                        e));
                }
                try {
                    if (Integer.parseInt(javaToolchainTargetVersion) < 9) {
                        javaToolchainTargetVersion = "1." + javaToolchainTargetVersion;
                    }
                } catch (NumberFormatException e) {
                    throw new CoreException(Status.error(
                        format("Unable to detect Java Toolchain information. Error parsing target level (%s)",
                            javaToolchainTargetVersion),
                        e));
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
                throw new CoreException(Status.error(
                    format("Unable to detect Java Toolchain information. Error parsing output of bazel cquery (%s)",
                        result),
                    e));
            }
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause != null) {
                throw new CoreException(Status.error(cause.getMessage(), cause));
            }

            throw new CoreException(Status.error(e.getMessage(), e));
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted while waiting for bazel cquery output to complete.");
        }

    }

    protected IWorkspace getEclipseWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    protected IWorkspaceRoot getEclipseWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    /**
     * @return the {@link BazelProjectFileSystemMapper} (only set during provisioning of a single target)
     */
    protected BazelProjectFileSystemMapper getFileSystemMapper() {
        return requireNonNull(fileSystemMapper,
            "file system mapper not initialized, check code flow/implementation (likely a bug)");
    }

    private void linkJavaSourcesIntoProject(BazelTarget target, BazelProject project, JavaInfo javaInfo,
            IProgressMonitor progress) throws CoreException {
        var monitor = SubMonitor.convert(progress);
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
                    file.setPersistentProperty(BazelProject.PROJECT_PROPERTY_TARGETS, target.getLabel().toString());
                    file.setPersistentProperty(BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT,
                        target.getBazelWorkspace().getLocation().toString());

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
        } finally {
            progress.done();
        }
    }

    protected BazelProject provisionJavaBinaryProject(BazelTarget target, IProgressMonitor progress)
            throws CoreException {

        // TODO: create a shared launch configuration

        return provisionJavaLibraryProject(target, progress);
    }

    protected BazelProject provisionJavaImportProject(BazelTarget target, IProgressMonitor progress)
            throws CoreException {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Provisions a Java project for the specified {@link BazelTarget}
     *
     * @param target
     *            the <code>java_library</code> target
     * @param progress
     *            monitor for reporting progress and tracking cancellation
     * @return the provisioned project
     * @throws CoreException
     */
    protected BazelProject provisionJavaLibraryProject(BazelTarget target, IProgressMonitor progress)
            throws CoreException {
        var monitor = SubMonitor.convert(progress, format("Provision project for target %s", target.getLabel()), 4);
        try {
            var project = provisionTargetProject(target, monitor.newChild(1));

            // build the Java information
            var javaInfo = collectJavaInfo(target, project, monitor.newChild(1));

            // configure links
            linkJavaSourcesIntoProject(target, project, javaInfo, monitor.newChild(1));

            // configure classpath
            configureRawClasspath(target, project, javaInfo, monitor.newChild(1));

            return project;
        } finally {
            progress.done();
        }
    }

    protected BazelProject provisionProjectForTarget(BazelTarget target, SubMonitor monitor) throws CoreException {
        var ruleName = target.getRuleClass();
        return switch (ruleName) {
            case "java_library": {
                yield provisionJavaLibraryProject(target, monitor);
            }
            case "java_import": {
                yield provisionJavaImportProject(target, monitor);
            }
            case "java_binary": {
                yield provisionJavaBinaryProject(target, monitor);
            }
            default: {
                LOG.debug("{}: Skipping provisioning due to unsupported rule '{}'.", target, ruleName);
                yield null;
            }
        };
    }

    @Override
    public List<BazelProject> provisionProjectsForSelectedTargets(Collection<BazelTarget> targets,
            BazelWorkspace workspace, IProgressMonitor progress) throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, "Provisioning projects", targets.size());

            // cleanup markers at workspace level
            workspace.getBazelProject().getProject().deleteMarkers(BUILDPATH_PROBLEM_MARKER, true,
                IResource.DEPTH_ZERO);

            // detect default Java level
            detectDefaultJavaToolchain(workspace);

            List<BazelProject> result = new ArrayList<>();
            for (BazelTarget target : targets) {
                monitor.subTask(target.getLabel().toString());

                // ensure there is a mapper
                if (fileSystemMapper == null) {
                    fileSystemMapper = new BazelProjectFileSystemMapper(workspace);
                }

                // provision project
                var project = provisionProjectForTarget(target, monitor.newChild(1));
                if (project != null) {
                    result.add(project);
                }
            }

            // after provisioning we go over the projects a second time to
            // populate all projects with links and configure the classpath
            for (BazelProject bazelProject : result) {
                computeClasspath(bazelProject, BazelClasspathScope.DEFAULT_CLASSPATH, monitor.newChild(1));
            }

            // done
            return result;
        } finally {
            progress.done();
            fileSystemMapper = null; // reset mapper
        }
    }

    protected BazelProject provisionTargetProject(BazelTarget target, IProgressMonitor progress) throws CoreException {
        var monitor = SubMonitor.convert(progress, 4);
        try {
            if (target.hasBazelProject()) {
                return target.getBazelProject();
            }

            var label = target.getLabel();
            var projectName = format("%s:%s", label.getPackagePath().replace('/', '.'), label.getTargetName());
            var projectDescription = getEclipseWorkspace().newProjectDescription(projectName);

            // place the project into the Bazel workspace project area
            var projectLocation = getFileSystemMapper().getProjectsArea().append(projectName);
            projectDescription.setLocation(projectLocation);
            projectDescription.setComment("Bazel Target Project managed by Bazel Eclipse Feature");

            // create project
            var project = getEclipseWorkspaceRoot().getProject(projectName);
            project.create(projectDescription, monitor.newChild(1));

            project.open(monitor.newChild(1));

            // set natures separately in order to ensure they are configured properly
            projectDescription = project.getDescription();
            projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID, BAZEL_NATURE_ID });
            project.setDescription(projectDescription, monitor.newChild(1));

            // set properties
            var workspaceRoot = target.getBazelWorkspace().getLocation();
            project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT, workspaceRoot.toString());
            project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_TARGETS, label.toString());

            // this call is no longer expected to fail now (unless we need to poke the element info cache manually here)
            return target.getBazelProject();
        } finally {
            progress.done();
        }
    }

}
