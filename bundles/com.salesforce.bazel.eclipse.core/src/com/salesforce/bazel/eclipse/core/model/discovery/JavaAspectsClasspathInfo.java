/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0, Apache-2.0
 *
 * Contributors:
 *      Salesforce - Initial implementation
 *      The Bazel Authors - parts of the code come from Bazel IJ plug-in, which were original Apache-2.0 licensed
*/
package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.toList;
import static org.eclipse.core.runtime.IPath.forPosix;
import static org.eclipse.core.runtime.IPath.fromPath;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.view.proto.Deps;
import com.google.devtools.build.lib.view.proto.Deps.Dependency.Kind;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.importer.ExecutionPathHelper;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.salesforce.bazel.eclipse.core.classpath.ClasspathHolder;
import com.salesforce.bazel.eclipse.core.classpath.ClasspathHolder.ClasspathHolderBuilder;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.AccessRule;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;
import com.salesforce.bazel.sdk.command.querylight.BazelRuleAttribute;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Holds information for computing Java classpath configuration of a target or a package.
 * <p>
 * An instance of this class must be initialized with the output of {@link BazelBuildWithIntelliJAspectsCommand build
 * with aspects result}. This result will be used for computing classpath.
 * </p>
 */
public class JavaAspectsClasspathInfo extends JavaClasspathJarLocationResolver {

    static record JdepsDependency(
            ArtifactLocation artifactLocation,
            com.google.devtools.build.lib.view.proto.Deps.Dependency.Kind dependencyKind) {
    }

    private static final Path PATTERN_EVERYTHING = new Path("**");

    private static Logger LOG = LoggerFactory.getLogger(JavaAspectsClasspathInfo.class);

    /**
     * Uses a filename heuristic to guess the location of a source jar corresponding to the given output jar.
     */
    private static ArtifactLocation guessSrcJarLocation(ArtifactLocation outputJar) {
        // copied from BlazeJavaWorkspaceImporter
        var srcJarRelPath = guessSrcJarRelativePath(outputJar.getRelativePath());
        if (srcJarRelPath == null) {
            return null;
        }
        // we don't check whether the source jar actually exists, to avoid unnecessary file system
        // operations
        return ArtifactLocation.Builder.copy(outputJar).setRelativePath(srcJarRelPath).build();
    }

    private static String guessSrcJarRelativePath(String relPath) {
        // copied from BlazeJavaWorkspaceImporter
        if (relPath.endsWith("-hjar.jar")) {
            return relPath.substring(0, relPath.length() - "-hjar.jar".length()) + "-src.jar";
        }
        if (relPath.endsWith("-ijar.jar")) {
            return relPath.substring(0, relPath.length() - "-ijar.jar".length()) + "-src.jar";
        }
        return null;
    }

    static boolean isJavaProtoTarget(TargetIdeInfo target) {
        return (target.getJavaIdeInfo() != null)
                && (JavaBlazeRules.getJavaProtoLibraryKinds().contains(target.getKind())
                        || target.getKind().equals(GenericBlazeRules.RuleTypes.PROTO_LIBRARY.getKind()));
    }

    final JavaAspectsInfo aspectsInfo;

    /** set of generated jars from source generators or annotation processors (maintaining insertion order) */
    final Set<BlazeJarLibrary> generatedCodeJars = new LinkedHashSet<>();

    /** set of compile jars from jdeps file (maintaining insertion order) */
    final Set<JdepsDependency> jdepsCompileJars = new LinkedHashSet<>();

    /** set of direct dependencies from jdeps file (maintaining insertion order) */
    final Set<TargetKey> directDeps = new LinkedHashSet<>();

    /** set of runtime dependencies (maintaining insertion order) */
    final Set<TargetKey> runtimeDeps = new LinkedHashSet<>();

    /** set of exports (insertion order is not relevant) */
    final Set<Label> exports = new HashSet<>();

    /** bazel project for this aspect */
    final BazelProject bazelProject;

    /**
     * Set of dependencies available to the whole project, to be used to filter out runtime dependencies that are not
     * part of this set. Allows creating a partial classpath to improve performance on large projects. Null indicates
     * full classpath to be used
     */
    final /*Nullable*/ Set<BazelLabel> runtimeDependencyIncludes;

    public JavaAspectsClasspathInfo(JavaAspectsInfo aspectsInfo, BazelWorkspace bazelWorkspace,
            Set<BazelLabel> runtimeDependencyIncludes, BazelProject bazelProject) throws CoreException {
        super(bazelWorkspace);
        this.aspectsInfo = aspectsInfo;
        this.runtimeDependencyIncludes = runtimeDependencyIncludes;
        this.bazelProject = bazelProject;
    }

    private void addDirectDependency(Dependency directDependency) {
        switch (directDependency.getDependencyType()) {
            case COMPILE_TIME: {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found direct compile dependency: {}", directDependency.getTargetKey());
                }
                directDeps.add(directDependency.getTargetKey());
                break;
            }
            case RUNTIME: {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found direct runtime dependency: {}", directDependency.getTargetKey());
                }
                runtimeDeps.add(directDependency.getTargetKey());
                break;
            }
            default: {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Ignoring dependency: {}", directDependency.getTargetKey());
                }
                break;
            }
        }
    }

    /**
     * Adds a target to be resolved.
     * <p>
     * When called multiple times, duplicate entries are eliminated. However, actual classpath ordering becomes
     * challenging. We try to maintain a predictable order. Hence, the implementation uses collections maintaining
     * insertion order at minimum. Any kind of sorting is not done.
     * </p>
     *
     * @param bazelTarget
     *            the target to resolve
     * @return status object indicating if there was a problem handling the target
     */
    public IStatus addTarget(BazelTarget bazelTarget) throws CoreException {
        var targetLabel = bazelTarget.getLabel().toPrimitive();
        var targetKey = TargetKey.forPlainTarget(targetLabel);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding target '{}' to classpath", targetLabel);
        }

        var targetIdeInfo = aspectsInfo.get(targetKey);
        if (targetIdeInfo == null) {
            //  this could be a failing build, abort with an error so it gets properly recored on the build path
            return Status.error(
                format(
                    "Unable to compute classpath for target '%s' because of missing IDE info! Please check the Bazel build for problems building the target.",
                    bazelTarget.getLabel()));
        }

        var javaIdeInfo = targetIdeInfo.getJavaIdeInfo();
        if (javaIdeInfo == null) {
            //  this could be a failing build, abort with an error so it gets properly recored on the build path
            return Status.error(
                format(
                    "Unable to compute classpath for target '%s' because of missing Java IDE info! Please check the Bazel build for problems building the target.",
                    bazelTarget.getLabel()));
        }

        // the following is inspired from IJ BlazeJavaWorkspaceImporter
        // we should probably check regularly for changes there

        // process generated sources
        javaIdeInfo.getGeneratedJars().stream().map(jar -> new BlazeJarLibrary(jar, targetKey)).forEach(jar -> {
            // it seems to contain generated classes but where do they come from?
            // according to Bazel doc it should be from annotation processing
            // (JavaInfo.generated_class_jar https://bazel.build/versions/6.0.0/rules/lib/JavaInfo)
            // if that is true, it would mean duplication with filtered-gen jar below - but that doesn't seem to be the case
            // however, I found stuff in there, which is also exist as '.java' file in 'srcs' of target; some bug in Bazel?
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found generated jar: {}", jar);
            }
            generatedCodeJars.add(jar);
        });
        if (javaIdeInfo.getFilteredGenJar() != null) {
            // the filtered-gen jar is produced by the IJ aspects
            // it contains classes produced by annotation processors
            // it also contains additional classes produced from '.srcjar' files in the 'srcs' list
            // ideally we would setup plugins on the project so we don't need this
            // we also need a solution for 'srcjar' jars to allow compiling them directly in Eclipse (if we want to get rid of aspects)
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found filtered gen jar: {}", javaIdeInfo.getFilteredGenJar());
            }
            generatedCodeJars.add(new BlazeJarLibrary(javaIdeInfo.getFilteredGenJar(), targetKey));
        }

        // special handling for protobuf targets
        // see also: https://github.com/bazelbuild/intellij/blob/53cf0680dab7ea2dac3c1589ac69b268d596aee3/java/src/com/google/idea/blaze/java/sync/importer/BlazeJavaWorkspaceImporter.java#L314
        if (isJavaProtoTarget(targetIdeInfo)) {
            // add generated jars from all proto library targets in the project
            javaIdeInfo.getJars().stream().map(jar -> new BlazeJarLibrary(jar, targetKey)).forEach(jar -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found proto library jar: {}", jar);
                }
                generatedCodeJars.add(jar);
            });
        }

        // process jdeps as actually being used by the compile step
        var jdeps = loadJdeps(targetIdeInfo);
        for (JdepsDependency jdep : jdeps) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found jdeps compile dependency: {}", jdep);
            }
            jdepsCompileJars.add(jdep);
        }

        // process direct dependencies
        for (Dependency directDependency : targetIdeInfo.getDependencies()) {
            var dependencyIdeInfo = aspectsInfo.get(directDependency.getTargetKey());
            if (dependencyIdeInfo == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Ignoring dependency without IDE info: {}", directDependency.getTargetKey());
                }
                continue;
            }

            addDirectDependency(directDependency);

            // special handling for protobuf targets
            if (isJavaProtoTarget(dependencyIdeInfo)) {
                // add all their dependencies as direct dependencies as well
                // this is needed to address an indirection created by bazel_java_proto_aspect
                // note: ideally this would already be part of the jdeps file; however, it's not (see libbuildjar.jdeps in https://github.com/salesforce/bazel-jdt-java-toolchain/blob/main/compiler/BUILD)
                dependencyIdeInfo.getDependencies().forEach(this::addDirectDependency);
            }
        }

        // (transitive) runtime dependencies
        var runtimeClasspath = aspectsInfo.getRuntimeClasspath(targetKey);
        if (runtimeClasspath != null) {
            for (BlazeJarLibrary jarLibrary : runtimeClasspath) {
                if (runtimeDeps.add(jarLibrary.targetKey) && LOG.isDebugEnabled()) {
                    LOG.debug("Found transitive runtime dependency: {}", jarLibrary.targetKey);
                }
            }
        }

        // collect exports
        var exports = bazelTarget.getRuleAttributes().getStringList(BazelRuleAttribute.EXPORTS);
        if (exports != null) {
            for (String export : exports) {
                this.exports.add(Label.create(export));
            }
        }

        return Status.OK_STATUS;
    }

    /**
     * Computes the classpath based on the {@link #addTarget(BazelTarget) added targets}.
     *
     * @return the computed classpath
     * @throws CoreException
     */
    public ClasspathHolder compute() throws CoreException {
        // the code below is copied and adapted from BlazeJavaWorkspaceImporter

        var classpathBuilder = new ClasspathHolderBuilder(runtimeDependencyIncludes != null);

        // Collect jars from jdep references
        for (JdepsDependency jdepsDependency : jdepsCompileJars) {
            var artifact = jdepsDependency.artifactLocation();
            var library = aspectsInfo.getLibraryByJdepsRootRelativePath(artifact.getRelativePath());
            if (library == null) {
                // It's in the target's jdeps, but our aspect never attached to the target building it.
                // Perhaps it's an implicit dependency, or not referenced in an attribute we propagate
                // along. Make a best-effort attempt to add it to the project anyway.
                var srcJar = guessSrcJarLocation(artifact);
                var srcJars = srcJar != null ? List.of(srcJar) : List.<ArtifactLocation> of();
                library = new BlazeJarLibrary(new LibraryArtifact(artifact, null, srcJars), /* targetKey= */ null);
            }
            var entry = resolveLibrary(library);
            if (entry != null) {
                if (!validateEntry(entry)) {
                    continue;
                }
                if (jdepsDependency.dependencyKind == Kind.IMPLICIT) {
                    entry.getAccessRules()
                            .add(
                                new AccessRule(
                                        PATTERN_EVERYTHING,
                                        IAccessRule.K_DISCOURAGED | IAccessRule.IGNORE_IF_BETTER));

                    // there might be an explicit entry, which we will never want to override!
                    classpathBuilder.putIfAbsent(entry.getPath(), entry);
                } else {
                    entry.getAccessRules().add(new AccessRule(PATTERN_EVERYTHING, IAccessRule.K_ACCESSIBLE));
                    classpathBuilder.put(entry.getPath(), entry);
                }
            } else if (LOG.isDebugEnabled()) {
                LOG.warn("Unable to resolve compile jar: {}", jdepsDependency);
            }
        }

        // Collect generated jars from source rules
        for (BlazeJarLibrary library : generatedCodeJars) {
            var jarEntry = resolveJar(library.libraryArtifact);
            if (jarEntry == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.warn("Unable to resolve generated source jar: {}", library.libraryArtifact);
                }
                continue;
            }
            if (!validateEntry(jarEntry)) {
                continue;
            }
            jarEntry.setExported(true); // source jars should be exported
            classpathBuilder.putIfAbsent(jarEntry.getPath(), jarEntry);
        }

        // Collect jars referenced by direct deps
        for (TargetKey targetKey : directDeps) {
            var entries = resolveDependency(targetKey);
            for (ClasspathEntry entry : entries) {
                if (validateEntry(entry)) {
                    classpathBuilder.putIfAbsent(entry.getPath(), entry);
                }
            }
        }

        // Collect jars referenced by runtime deps
        for (TargetKey targetKey : runtimeDeps) {
            var entries = resolveDependency(targetKey);
            var runtimeDependencyAvailable = includeRuntimeDependencyAsCompileDependency(targetKey);

            for (ClasspathEntry entry : entries) {
                if (!validateEntry(entry)) {
                    continue;
                }
                // runtime dependencies are only visible to tests
                entry.getExtraAttributes().put(IClasspathAttribute.TEST, Boolean.toString(true));
                // runtime dependencies are never accessible
                entry.getAccessRules()
                        .add(
                            new AccessRule(
                                    PATTERN_EVERYTHING,
                                    IAccessRule.K_DISCOURAGED | IAccessRule.IGNORE_IF_BETTER));
                if (runtimeDependencyAvailable) {
                    classpathBuilder.putIfAbsent(entry.getPath(), entry);
                } else {
                    classpathBuilder.putUnloadedIfAbsent(entry.getPath(), entry);
                }
            }
        }

        return classpathBuilder.build();
    }

    private BazelWorkspace findExternalWorkspace(Label label) throws CoreException {
        var externalWorkspaceName = label.externalWorkspaceName();
        if (externalWorkspaceName.startsWith("@")) {
            // we have a canonical repository name; try to see if it links to workspace in the IDE
            // IJ does similar thing: https://github.com/bazelbuild/intellij/blob/8b64eb559811a803e07bc07c278168f3607c0f50/base/src/com/google/idea/blaze/base/sync/workspace/WorkspaceHelper.java#L169-L184
            try {
                var externalWorkspacePath =
                        getBlazeInfo().getOutputBase().resolve("external").resolve(externalWorkspaceName.substring(1));
                if (exists(externalWorkspacePath)) {
                    var path = fromPath(externalWorkspacePath.toRealPath());
                    var externalWorkspace = bazelWorkspace.getParent().getBazelWorkspace(path);
                    if (externalWorkspace.exists()) {
                        return externalWorkspace;
                    }
                }
            } catch (IOException e) {
                throw new CoreException(
                        Status.error(
                            format(
                                "Error while resolving canonical repository name '%s': %s",
                                externalWorkspaceName.substring(1),
                                e.getMessage()),
                            e));
            }
        }

        // lookup and see if it maps to 'local_repository'
        var externalRepository = bazelWorkspace.getExternalRepository(externalWorkspaceName);
        if (externalRepository != null) {
            switch (externalRepository.getRuleClass()) {
                case "local_repository": {
                    var pathAttribute = externalRepository.getString(BazelRuleAttribute.PATH);
                    if (pathAttribute != null) {
                        var path = forPosix(pathAttribute);
                        if (!path.isAbsolute()) {
                            path = bazelWorkspace.getLocation().append(path);
                        }

                        var externalWorkspace = bazelWorkspace.getParent().getBazelWorkspace(path);
                        if (externalWorkspace.exists()) {
                            return externalWorkspace;
                        }
                    }
                }
            }
        }

        LOG.debug("External workspace '{}' not found in current IDE workspace.", externalWorkspaceName);
        return null;
    }

    private String findProjectMapping(final Label targetLabel) throws CoreException {
        // direct patch preferred
        var projectMapping = bazelWorkspace.getBazelProjectView().projectMappings().get(targetLabel.toString());
        if (projectMapping != null) {
            return projectMapping;
        }

        // just check for the repo name if label is verbose "@some_target//:some_target"
        if ((targetLabel.blazePackage().isWorkspaceRoot())
                && targetLabel.targetName().toString().equals(targetLabel.externalWorkspaceName())) {
            projectMapping = bazelWorkspace.getBazelProjectView()
                    .projectMappings()
                    .get("@" + targetLabel.externalWorkspaceName());
        }
        return projectMapping;
    }

    IWorkspaceRoot getEclipseWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    /**
     * Allows filtering runtime dependencies based on the availability of the dependency in runtimseDependencyIncludes
     * when it includes elements
     *
     * @param targetKey
     *            the dependency to check
     * @return true if runtimeDependencyAvailable is empty or it includes the dependency
     */
    private boolean includeRuntimeDependencyAsCompileDependency(TargetKey targetKey) {
        return (runtimeDependencyIncludes == null)
                || runtimeDependencyIncludes.contains(new BazelLabel(targetKey.getLabel().toString()));
    }

    private List<JdepsDependency> loadJdeps(TargetIdeInfo targetIdeInfo) throws CoreException {
        // load jdeps file
        var jdepsFile = resolveJdepsOutput(targetIdeInfo);
        if (jdepsFile instanceof OutputArtifact) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Loading jdeps file '{}' for: {}", jdepsFile, targetIdeInfo.getKey());
            }
            try (InputStream inputStream = jdepsFile.getInputStream()) {
                var dependencies = Deps.Dependencies.parseFrom(inputStream);
                if (dependencies != null) {
                    return dependencies.getDependencyList()
                            .stream()
                            .filter(this::relevantDep)
                            .map(
                                d -> new JdepsDependency(
                                        ExecutionPathHelper
                                                .parse(workspaceRoot, BazelBuildSystemProvider.BAZEL, d.getPath()),
                                        d.getKind()))
                            .collect(toList());
                }
            } catch (IOException e) {
                throw new CoreException(Status.error(format("Error reading jdeps file '%s'.", jdepsFile), e));
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("No jdeps file/data for: {}", targetIdeInfo.getKey());
        }
        return List.of();
    }

    protected ClasspathEntry newProjectReference(Label targetLabel, BazelProject bazelProject) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found workspace reference for '{}': {}", targetLabel, bazelProject.getProject());
        }
        var entry = ClasspathEntry.newProjectEntry(bazelProject.getProject());
        entry.setExported(exports.contains(targetLabel));
        return entry;
    }

    protected boolean relevantDep(Deps.Dependency dep) {
        // we only want explicit or implicit deps that were actually resolved by the compiler, not ones
        // that are available for use in the same package
        return (dep.getKind() == Deps.Dependency.Kind.EXPLICIT) || (dep.getKind() == Deps.Dependency.Kind.IMPLICIT);
    }

    protected Collection<ClasspathEntry> resolveDependency(TargetKey targetKey) throws CoreException {
        var projectEntry = resolveProject(targetKey);
        if (projectEntry != null) {
            return Set.of(projectEntry);
        }

        var jars = aspectsInfo.getLibraries(targetKey);
        if (jars == null) {
            if (LOG.isDebugEnabled()) {
                LOG.warn("Unable to locate compile jars in index for dependency: {}", targetKey);
            }
            return Collections.emptyList();
        }
        var exported = exports.contains(targetKey.getLabel());
        var result = new LinkedHashSet<ClasspathEntry>();
        for (BlazeJarLibrary library : jars) {

            var jarEntry = resolveJar(library.libraryArtifact);
            if (jarEntry == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.warn("Unable to resolve compile jar: {}", library.libraryArtifact);
                }
                continue;
            }
            jarEntry.setExported(exported);
            result.add(jarEntry);
        }
        return result;
    }

    protected BlazeArtifact resolveJdepsOutput(TargetIdeInfo target) {
        var javaIdeInfo = target.getJavaIdeInfo();
        if ((javaIdeInfo == null) || (javaIdeInfo.getJdepsFile() == null)) {
            return null;
        }
        return locationDecoder.resolveOutput(javaIdeInfo.getJdepsFile());
    }

    private ClasspathEntry resolveLibrary(BlazeJarLibrary library) throws CoreException {
        // find project in workspace if possible
        if (library.targetKey != null) {
            var projectEntry = resolveProject(library.targetKey);
            if (projectEntry != null) {
                return projectEntry;
            }
        }

        // resolve as jar
        return resolveJar(library.libraryArtifact);
    }

    public ClasspathEntry resolveProject(final Label targetLabel) throws CoreException {
        var workspace = bazelWorkspace;

        // check for project mapping (it trumps everything)
        var projectMapping = findProjectMapping(targetLabel);
        if (projectMapping != null) {
            try {
                var projectMappingUri = new URI(projectMapping);
                var scheme = projectMappingUri.getScheme();
                if ("project".equals(scheme)) {
                    var projectName = projectMappingUri.getSchemeSpecificPart();
                    LOG.debug(
                        "Discovered project mapping for target '{}': {} (path '{}')",
                        targetLabel,
                        projectMapping,
                        projectName);
                    if (projectName != null) {
                        var project = getEclipseWorkspaceRoot().getProject(projectName);
                        if (project.isAccessible()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Found project reference for '{}': {}", targetLabel, project.getProject());
                            }
                            return ClasspathEntry.newProjectEntry(project);
                        }
                    }
                } else {
                    LOG.warn(
                        "Found a project mapping '{}' for target '{}' but the scheme '{}' is not supported. Only 'project' is supported at this time.",
                        projectMapping,
                        targetLabel,
                        scheme);
                    // fall through
                }
            } catch (URISyntaxException e) {
                LOG.error(
                    "Unparsable project mapping for target '{}': {} (check project view of '{}')",
                    targetLabel,
                    projectMapping,
                    workspace,
                    e);
            }
        }

        if (targetLabel.isExternal()) {
            workspace = findExternalWorkspace(targetLabel);
            if (workspace == null) {
                return null;
            }
        }
        var bazelPackage = workspace.getBazelPackage(forPosix(targetLabel.blazePackage().relativePath()));
        var bazelTarget = bazelPackage.getBazelTarget(targetLabel.targetName().toString());
        if (bazelTarget.hasBazelProject() && bazelTarget.getBazelProject().getProject().isAccessible()) {
            // a direct target match is preferred
            return newProjectReference(targetLabel, bazelTarget.getBazelProject());
        }
        if (bazelPackage.hasBazelProject() && bazelPackage.getBazelProject().getProject().isAccessible()) {
            // we have to check the target name is part of the enabled project list
            // otherwise it might be a special jar by some generator target we don't support for import
            var targetName = targetLabel.targetName().toString();
            if (bazelPackage.getBazelProject()
                    .getBazelTargets()
                    .stream()
                    .anyMatch(t -> t.getTargetName().equals(targetName))) {
                return newProjectReference(targetLabel, bazelPackage.getBazelProject());
            }
        }

        // nothing found
        return null;
    }

    protected ClasspathEntry resolveProject(TargetKey targetKey) throws CoreException {
        if (!targetKey.isPlainTarget()) {
            return null;
        }

        return resolveProject(targetKey.getLabel());
    }

    /**
     * Validates the classpath entry is valid and returns true if the entry should be included
     *
     * @param entry
     *            the classpath entry to validate and check
     * @return false if the entry should not be included in the classpath
     * @throws CoreException
     *             if validation fails
     */
    private boolean validateEntry(ClasspathEntry entry) throws CoreException {
        if ((entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) && !isRegularFile(entry.getPath().toPath())) {
            BaseProvisioningStrategy.createClasspathContainerProblem(
                bazelProject,
                Status.error(
                    format("Library '%s' is missing. Please consider running 'bazel fetch'", entry.getPath())));
            return false;
        }

        // remove references to the project represented by the package
        // (this can happen because we have tests and none tests in the same package, also the runtime CP self-reference)
        return (entry.getEntryKind() != IClasspathEntry.CPE_PROJECT)
                || !entry.getPath().equals(bazelProject.getProject().getFullPath());
    }

}
