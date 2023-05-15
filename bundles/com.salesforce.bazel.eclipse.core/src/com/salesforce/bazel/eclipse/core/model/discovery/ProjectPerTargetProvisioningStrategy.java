package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.view.proto.Deps;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspaceBlazeInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;

/**
 * Default implementation of {@link TargetProvisioningStrategy} which provisions a single project per supported target.
 * <p>
 * <ul>
 * <li>One Eclipse project is created per supported <code>java_*</code> target per package.</li>
 * <li>The build path is setup specifically for that target, allowing for best support in the IDE.</li>
 * <li>Projects are created in a project area (<code>.eclipse/projects</code> folder inside the workspace) and files are
 * created as links. This makes SCM not really functional for these.</li>
 * <li>Targets inside the root (empty) package <code>//:*</code> are supported.</li>
 * </ul>
 * </p>
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

    private void addJarToClasspath(ArtifactLocationDecoder decoder, LibraryArtifact jar, TargetKey dependencyTargetKey,
            List<ClasspathEntry> classpath) {
        var jarArtifactForIde = jar.jarForIntellijLibrary();
        if (jarArtifactForIde.isMainWorkspaceSourceArtifact()) {
            IPath jarPath = new Path(decoder.resolveSource(jarArtifactForIde).toString());
            var sourceJar = jar.getSourceJars().stream().findFirst();
            if (!sourceJar.isPresent()) {
                LOG.debug("'{}' -> jar '{}' without source", dependencyTargetKey, jarPath);
                classpath.add(ClasspathEntry.newLibraryEntry(jarPath, null, null, false /* test only */));
                return;
            }

            IPath srcJarPath = new Path(decoder.resolveSource(sourceJar.get()).toString());
            LOG.debug("'{}' -> jar '{}' (source '{}')", dependencyTargetKey, jarPath, srcJarPath);
            classpath.add(ClasspathEntry.newLibraryEntry(jarPath, srcJarPath, null, false /* test only */));
            return;
        }
        var jarArtifact = decoder.resolveOutput(jarArtifactForIde);
        if (jarArtifact instanceof LocalFileArtifact localJar) {
            IPath jarPath = new Path(localJar.getPath().toString());
            var sourceJar = jar.getSourceJars().stream().findFirst();
            if (!sourceJar.isPresent()) {
                LOG.debug("'{}' -> jar '{}' without source", dependencyTargetKey, jarPath);
                classpath.add(ClasspathEntry.newLibraryEntry(jarPath, null, null, false /* test only */));
                return;
            }
            var srcJarArtifact = decoder.resolveOutput(sourceJar.get());
            if (srcJarArtifact instanceof LocalFileArtifact localSrcJar) {
                IPath srcJarPath = new Path(localSrcJar.getPath().toString());
                LOG.debug("'{}' -> jar '{}' (source '{}')", dependencyTargetKey, jarPath, srcJarPath);
                classpath.add(ClasspathEntry.newLibraryEntry(jarPath, srcJarPath, null, false /* test only */));
            }
        }
    }

    @Override
    public List<ClasspathEntry> computeClasspath(BazelProject bazelProject, BazelClasspathScope scope,
            IProgressMonitor monitor) throws CoreException {
        LOG.debug("Computing classpath for project: {}", bazelProject);

        if (bazelProject.isWorkspaceProject()) {
            // FIXME: implement support for reading all jars from WORKSPACE
            //
            // For example:
            //   1. get list of all external repos
            //      > bazel query "//external:*"
            //   2. query for java rules for each external repo
            //      > bazel query "kind('java_.* rule', @exernal_repo_name//...)"
            //
            // or:
            //   1. specific support for jvm_import_external
            //      > bazel query "kind(jvm_import_external, //external:*)"
            //
            return List.of();
        }

        var bazelWorkspace = bazelProject.getBazelWorkspace();
        var workspaceRoot = bazelWorkspace.getLocation().toFile().toPath();

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

        // build map of targets and dependencies
        Map<TargetKey, TargetIdeInfo> targetMap = new HashMap<>();
        Map<TargetKey, List<String>> jdepsMap = new HashMap<>();
        ArtifactLocationDecoder decoder = new ArtifactLocationDecoderImpl(new BazelWorkspaceBlazeInfo(bazelWorkspace),
                new WorkspacePathResolverImpl(new WorkspaceRoot(workspaceRoot)));
        var outputArtifacts = result.getOutputGroupArtifacts(OutputGroup.INFO::isPrefixOf,
            IntellijAspects.ASPECT_OUTPUT_FILE_PREDICATE);
        for (OutputArtifact outputArtifact : outputArtifacts) {
            try {
                var info = TargetIdeInfo.fromProto(aspects.readAspectFile(outputArtifact));
                targetMap.put(info.getKey(), info);

                // load jdeps file
                var jdepsFile = resolveJdepsOutput(decoder, info);
                if (jdepsFile instanceof OutputArtifact) {
                    try (InputStream inputStream = jdepsFile.getInputStream()) {
                        var dependencies = Deps.Dependencies.parseFrom(inputStream);
                        if (dependencies == null) {
                            return null;
                        }
                        List<String> deps = dependencies.getDependencyList().stream()
                                .filter(ProjectPerTargetProvisioningStrategy::relevantDep).map(Deps.Dependency::getPath)
                                .collect(toList());
                        jdepsMap.put(info.getKey(), deps);
                    } catch (IOException e) {
                        throw new CoreException(Status.error(
                            format("Unable to compute classpath for project '%s'. Error reading jdeps file '%s'.",
                                bazelProject, jdepsFile),
                            e));
                    }

                }
            } catch (IOException e) {
                throw new CoreException(Status
                        .error(format("Unable to compute classpath for project '%s'. Error reading aspect file '%s'.",
                            bazelProject, outputArtifact), e));
            }
        }

        // the classpath entries
        var entries = new ArrayList<ClasspathEntry>();

        var projectTargetLabel = bazelProject.getBazelTarget().getLabel().toPrimitive();
        var targetIdeInfo = targetMap.get(TargetKey.forPlainTarget(projectTargetLabel));

        // process generated sources
        var javaIdeInfo = targetIdeInfo.getJavaIdeInfo();
        if (javaIdeInfo != null) {
            var generatedJars = javaIdeInfo.getGeneratedJars();
            for (LibraryArtifact generatedJar : generatedJars) {
                addJarToClasspath(decoder, generatedJar, targetIdeInfo.getKey(), entries);
            }
        }

        // process compile dependencies
        var dependencies = targetIdeInfo.getDependencies();
        for (Dependency dependency : dependencies) {
            switch (dependency.getDependencyType()) {
                case COMPILE_TIME:
                case RUNTIME:
                    LOG.debug("{} - Processing dependency: {}", projectTargetLabel, dependency.getTargetKey());
                    resolveDependencyAndAddToClasspath(bazelWorkspace, targetMap, decoder, dependency, entries);
                    break;

                case UNRECOGNIZED:
                default:
                    LOG.debug("{} - Ignoring uncategorized dependency: {}", projectTargetLabel,
                        dependency.getTargetKey());
                    break;
            }
        }

        // check for non existing jars
        for (ClasspathEntry entry : entries) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_LIBRARY) {
                continue;
            }

            if (!isRegularFile(entry.getPath().toFile().toPath())) {
                createBuildPathProblem(bazelProject,
                    Status.error("There are missing library. Please consider running 'bazel fetch'"));
                break;
            }
        }

        return entries;

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
            var javaInfo = collectJavaInfo(project, List.of(target), monitor.newChild(1));

            // configure links
            linkSourcesIntoProject(project, javaInfo, monitor.newChild(1));

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
        var projectLocation = getFileSystemMapper().getProjectsArea().append(projectName);

        var project = createProjectForElement(projectName, projectLocation, target, progress);
        project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_TARGETS, target.getLabel().getLabelPath());

        // this call is no longer expected to fail now (unless we need to poke the element info cache manually here)
        return target.getBazelProject();
    }

    private void resolveDependencyAndAddToClasspath(BazelWorkspace bazelWorkspace,
            Map<TargetKey, TargetIdeInfo> targetMap, ArtifactLocationDecoder decoder, Dependency dependency,
            List<ClasspathEntry> classpath) throws CoreException {
        var dependencyTargetKey = dependency.getTargetKey();
        if (dependencyTargetKey.isPlainTarget() && !dependencyTargetKey.getLabel().isExternal()) {
            var bazelPackage = bazelWorkspace
                    .getBazelPackage(new Path(dependencyTargetKey.getLabel().blazePackage().relativePath()));
            var bazelTarget = bazelPackage.getBazelTarget(dependencyTargetKey.getLabel().targetName().toString());
            if (bazelTarget.hasBazelProject()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found workspace reference for '{}': {}", dependencyTargetKey,
                        bazelTarget.getBazelProject().getProject());
                }
                classpath.add(ClasspathEntry.newProjectEntry(bazelTarget.getBazelProject().getProject()));
                return;
            }
        }

        var depInfo = targetMap.get(dependencyTargetKey);
        if (depInfo == null) {
            // this can happen when (for whatever reason) the target is not included/processed by the aspects
            LOG.warn("No aspect info returned for dependency '{}'", dependency);
            return;
        }

        if (JavaBlazeRules.getJavaProtoLibraryKinds().contains(depInfo.getKind())) {
            // special processing for protobuf dependencies
            // they have another level of indirection
            var protoDeps = depInfo.getDependencies();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Processing proto dependencies for '{}': {}", dependencyTargetKey, protoDeps);
            }
            for (Dependency protoDep : protoDeps) {
                resolveDependencyAndAddToClasspath(bazelWorkspace, targetMap, decoder, protoDep, classpath);
            }
            return;
        }

        var javaIdeInfo = depInfo.getJavaIdeInfo();
        if (javaIdeInfo == null) {
            LOG.debug("Ignoring dependency without Java info: {}", depInfo);
            return;
        }

        // add all jars
        // usually this is just one - however, java_import can have many
        for (LibraryArtifact jar : javaIdeInfo.getJars()) {
            addJarToClasspath(decoder, jar, dependencyTargetKey, classpath);
        }
    }

}
