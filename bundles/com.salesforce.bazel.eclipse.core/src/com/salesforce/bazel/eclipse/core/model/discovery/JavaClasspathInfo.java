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
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAccessRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.view.proto.Deps;
import com.google.devtools.build.lib.view.proto.Deps.Dependency.Kind;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.importer.ExecutionPathHelper;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.AccessRule;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;

/**
 * Holds information for computing Java classpath configuration of a target or a package.
 * <p>
 * An instance of this class must be initialized with the output of {@link BazelBuildWithIntelliJAspectsCommand build
 * with aspects result}. This result will be used for computing classpath.
 * </p>
 */
public class JavaClasspathInfo extends JavaClasspathJarInfo {

    static record JdepsDependency(
            ArtifactLocation artifactLocation,
            com.google.devtools.build.lib.view.proto.Deps.Dependency.Kind dependencyKind) {
    }

    private static final Path PATTERN_EVERYTHING = new Path("**");

    private static Logger LOG = LoggerFactory.getLogger(JavaClasspathInfo.class);

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

    final ParsedBepOutput aspectsBuildResult;

    /** index of all aspects loaded from the build output */
    final Map<TargetKey, TargetIdeInfo> ideInfoByTargetKey;

    /** index of all jars belonging to a target */
    final Map<TargetKey, List<BlazeJarLibrary>> librariesByTargetKey;

    /** index of jars based on their root relative path allows lookup of jdeps entries */
    final Map<String, BlazeJarLibrary> libraryByJdepsRootRelativePath;

    /** set of generated source jars (maintaining insertion order) */
    final Set<BlazeJarLibrary> generatedSourceJars = new LinkedHashSet<>();

    /** set of compile jars from jdeps file (maintaining insertion order) */
    final Set<JdepsDependency> jdepsCompileJars = new LinkedHashSet<>();

    /** set of direct dependencies from jdeps file (maintaining insertion order) */
    final Set<TargetKey> directDeps = new LinkedHashSet<>();

    /** set of runtime dependencies (maintaining insertion order) */
    final Set<TargetKey> runtimeDeps = new LinkedHashSet<>();

    public JavaClasspathInfo(ParsedBepOutput aspectsBuildResult, BazelWorkspace bazelWorkspace) throws CoreException {
        super(bazelWorkspace);
        this.aspectsBuildResult = aspectsBuildResult;

        // build maps
        ideInfoByTargetKey = new HashMap<>();
        librariesByTargetKey = new HashMap<>();
        libraryByJdepsRootRelativePath = new HashMap<>();

        // index all the info from each aspect
        var outputArtifacts = aspectsBuildResult.getOutputGroupArtifacts(OutputGroup.INFO::isPrefixOf,
            IntellijAspects.ASPECT_OUTPUT_FILE_PREDICATE);
        NEXT_ASPECT: for (OutputArtifact outputArtifact : outputArtifacts) {
            try {
                // parse the aspect
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Processing aspect: {}", outputArtifact);
                }
                var targetIdeInfo = TargetIdeInfo.fromProto(getAspects().readAspectFile(outputArtifact));
                if (targetIdeInfo == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Skipping empty aspect: {}", outputArtifact);
                    }
                    continue NEXT_ASPECT;
                }
                var javaIdeInfo = targetIdeInfo.getJavaIdeInfo();
                if (javaIdeInfo == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Skipping aspect without Java info: {}", outputArtifact);
                    }
                    continue NEXT_ASPECT;
                }

                var targetKey = targetIdeInfo.getKey();
                ideInfoByTargetKey.put(targetKey, targetIdeInfo);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Indexing target: {}", targetKey);
                }

                // add all jars to our index (so we can map them back later)
                for (var jar : javaIdeInfo.getJars()) {
                    addLibrary(new BlazeJarLibrary(jar, targetKey));
                }
                for (var jar : javaIdeInfo.getGeneratedJars()) {
                    addLibrary(new BlazeJarLibrary(jar, targetKey));
                }
                if (javaIdeInfo.getFilteredGenJar() != null) {
                    addLibrary(new BlazeJarLibrary(javaIdeInfo.getFilteredGenJar(), targetKey));
                }

            } catch (IOException e) {
                throw new CoreException(Status.error(format("Error reading aspect file '%s'.", outputArtifact), e));
            }
        }
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

    private void addLibrary(BlazeJarLibrary library) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Indexing jar: {}", library);
        }

        librariesByTargetKey.computeIfAbsent(library.targetKey, t -> new ArrayList<>());
        librariesByTargetKey.get(library.targetKey).add(library);

        var libraryArtifact = library.libraryArtifact;
        var interfaceJar = libraryArtifact.getInterfaceJar();
        if (interfaceJar != null) {
            libraryByJdepsRootRelativePath.put(interfaceJar.getRelativePath(), library);
        }
        var classJar = libraryArtifact.getClassJar();
        if (classJar != null) {
            libraryByJdepsRootRelativePath.put(classJar.getRelativePath(), library);
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
     */
    public void addTarget(BazelTarget bazelTarget) throws CoreException {
        var targetLabel = bazelTarget.getLabel().toPrimitive();
        var targetKey = TargetKey.forPlainTarget(targetLabel);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding target '{}' to classpath", targetLabel);
        }

        var targetIdeInfo = ideInfoByTargetKey.get(targetKey);
        if (targetIdeInfo == null) {
            //  this could be a failing build, abort with a warning
            LOG.warn(
                "Unable to compute classpath for target '{}' because of missing IDE info! Please check the Bazel build for problems building the target.",
                bazelTarget.getLabel());
            return;
        }

        var javaIdeInfo = targetIdeInfo.getJavaIdeInfo();
        if (javaIdeInfo == null) {
            //  this could be a failing build, abort with a warning
            LOG.warn(
                "Unable to compute classpath for target '{}' because of missing Java IDE info! Please check the Bazel build for problems building the target.",
                bazelTarget.getLabel());
            return;
        }

        // the following is inspired from IJ BlazeJavaWorkspaceImporter
        // we should probably check regularly for changes there

        // process generated sources
        javaIdeInfo.getGeneratedJars().stream().map(jar -> new BlazeJarLibrary(jar, targetKey)).forEach(jar -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found generated jar: {}", jar);
            }
            generatedSourceJars.add(jar);
        });
        if (javaIdeInfo.getFilteredGenJar() != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found filtered gen jar: {}", javaIdeInfo.getFilteredGenJar());
            }
            generatedSourceJars.add(new BlazeJarLibrary(javaIdeInfo.getFilteredGenJar(), targetKey));
        }

        // special handling for protobuf targets
        if (isJavaProtoTarget(targetIdeInfo)) {
            // add generated jars from all proto library targets in the project
            javaIdeInfo.getJars().stream().map(jar -> new BlazeJarLibrary(jar, targetKey)).forEach(jar -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found proto library jar: {}", jar);
                }
                generatedSourceJars.add(jar);
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
            var dependencyIdeInfo = ideInfoByTargetKey.get(directDependency.getTargetKey());
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
    }

    /**
     * Computes the classpath based on the {@link #addTarget(BazelTarget) added targets}.
     *
     * @return the computed classpath
     * @throws CoreException
     */
    public Collection<ClasspathEntry> compute() throws CoreException {
        // the code below is copied and adapted from BlazeJavaWorkspaceImporter

        // Preserve classpath order. Add leaf level dependencies first and work the way up. This
        // prevents conflicts when a JAR repackages it's dependencies. In such a case we prefer to
        // resolve symbols from the original JAR rather than the repackaged version.
        // Using accessOrdered LinkedHashMap because jars that are present in `workspaceBuilder.jdeps`
        // and in `workspaceBuilder.directDeps`, we want to treat it as a directDep
        Map<IPath, ClasspathEntry> result =
                new LinkedHashMap<>(/* initialCapacity= */ 32, /* loadFactor= */ 0.75f, /* accessOrder= */ true);

        // Collect jars from jdep references
        for (JdepsDependency jdepsDependency : jdepsCompileJars) {
            var artifact = jdepsDependency.artifactLocation();
            var library = libraryByJdepsRootRelativePath.get(artifact.getRelativePath());
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
                if (jdepsDependency.dependencyKind == Kind.IMPLICIT) {
                    entry.getAccessRules().add(
                        new AccessRule(PATTERN_EVERYTHING, IAccessRule.K_DISCOURAGED | IAccessRule.IGNORE_IF_BETTER));

                    // there might be an explicit entry, which we will never want to override!
                    result.putIfAbsent(entry.getPath(), entry);
                } else {
                    entry.getAccessRules().add(new AccessRule(PATTERN_EVERYTHING, IAccessRule.K_ACCESSIBLE));
                    result.put(entry.getPath(), entry);
                }
            } else if (LOG.isDebugEnabled()) {
                LOG.warn("Unable to resolve compile jar: {}", jdepsDependency);
            }
        }

        // Collect jars referenced by direct deps
        for (TargetKey targetKey : directDeps) {
            var projectEntry = resolveProject(targetKey);
            if (projectEntry != null) {
                result.put(projectEntry.getPath(), projectEntry);
                continue;
            }

            var jars = librariesByTargetKey.get(targetKey);
            if (jars == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.warn("Unable to locate compile jars in index for dependency: {}", targetKey);
                }
                continue;
            }
            for (BlazeJarLibrary library : jars) {
                var jarEntry = resolveJar(library.libraryArtifact);
                if (jarEntry == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.warn("Unable to resolve compile jar: {}", library.libraryArtifact);
                    }
                    continue;
                }
                result.put(jarEntry.getPath(), jarEntry);
            }
        }

        // Collect generated jars from source rules
        for (BlazeJarLibrary library : generatedSourceJars) {
            var jarEntry = resolveJar(library.libraryArtifact);
            if (jarEntry == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.warn("Unable to resolve generated source jar: {}", library.libraryArtifact);
                }
                continue;
            }
            result.put(jarEntry.getPath(), jarEntry);
        }

        return result.values();
    }

    public IntellijAspects getAspects() {
        return bazelWorkspace.getParent().getModelManager().getIntellijAspects();
    }

    private List<JdepsDependency> loadJdeps(TargetIdeInfo targetIdeInfo) throws CoreException {
        // load jdeps file
        var jdepsFile = resolveJdepsOutput(targetIdeInfo);
        if (jdepsFile instanceof OutputArtifact) {
            try (InputStream inputStream = jdepsFile.getInputStream()) {
                var dependencies = Deps.Dependencies.parseFrom(inputStream);
                if (dependencies != null) {
                    return dependencies.getDependencyList().stream().filter(this::relevantDep).map(
                        d -> new JdepsDependency(
                                ExecutionPathHelper.parse(workspaceRoot, BazelBuildSystemProvider.BAZEL, d.getPath()),
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

    protected boolean relevantDep(Deps.Dependency dep) {
        // we only want explicit or implicit deps that were actually resolved by the compiler, not ones
        // that are available for use in the same package
        return (dep.getKind() == Deps.Dependency.Kind.EXPLICIT) || (dep.getKind() == Deps.Dependency.Kind.IMPLICIT);
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

    private ClasspathEntry resolveProject(TargetKey targetKey) throws CoreException {
        if (targetKey.isPlainTarget() && !targetKey.getLabel().isExternal()) {
            var bazelPackage =
                    bazelWorkspace.getBazelPackage(new Path(targetKey.getLabel().blazePackage().relativePath()));
            var bazelTarget = bazelPackage.getBazelTarget(targetKey.getLabel().targetName().toString());
            if (bazelTarget.hasBazelProject()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found workspace reference for '{}': {}", targetKey,
                        bazelTarget.getBazelProject().getProject());
                }
                return ClasspathEntry.newProjectEntry(bazelTarget.getBazelProject().getProject());
            }
        }
        return null;

    }

}
