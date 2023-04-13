package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BUILDPATH_PROBLEM_MARKER;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
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
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.JavaInfo.FileEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;

/**
 * Default implementation of {@link TargetProvisioningStrategy} which provisions a single project per supported target.
 *
 * @since 2.0
 */
public class ProjectPerTargetProvisioningStrategy extends BaseProvisioningStrategy {

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
            createBuildPathProblem(target.getBazelWorkspace().getBazelProject(), Status.warning(
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

        var result = bazelWorkspace.getCommandExecutor().runDirectlyWithWorkspaceLock(command,
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

    @Override
    protected List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets, IProgressMonitor progress)
            throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, "Provisioning projects", targets.size());
            List<BazelProject> result = new ArrayList<>();
            for (BazelTarget target : targets) {
                monitor.subTask(target.getLabel().toString());

                // provision project
                var project = provisionProjectForTarget(target, monitor.newChild(1));
                if (project != null) {
                    result.add(project);
                }
            }
            return result;
        } finally {
            progress.done();
        }
    }

    protected void linkJavaSourcesIntoProject(BazelProject project, JavaInfo javaInfo, IProgressMonitor progress)
            throws CoreException {
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
            linkJavaSourcesIntoProject(project, javaInfo, monitor.newChild(1));

            // configure classpath
            configureRawClasspath(project, javaInfo, monitor.newChild(1));

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

    protected BazelProject provisionTargetProject(BazelTarget target, IProgressMonitor progress) throws CoreException {
        if (target.hasBazelProject()) {
            return target.getBazelProject();
        }

        var label = target.getLabel();
        var projectName = format("%s:%s", label.getPackagePath().replace('/', '.'), label.getTargetName());

        var project = createProjectForElement(projectName, target, progress);
        project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_TARGETS, target.getLabel().getLabelPath());

        // this call is no longer expected to fail now (unless we need to poke the element info cache manually here)
        return target.getBazelProject();
    }

}
