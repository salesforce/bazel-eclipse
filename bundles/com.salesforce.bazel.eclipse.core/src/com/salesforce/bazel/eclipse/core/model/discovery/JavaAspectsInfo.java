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
import static java.nio.file.Files.isReadable;
import static java.util.stream.Collectors.toList;
import static org.eclipse.core.runtime.IPath.fromPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.collect.nestedset.NestedSetVisitor;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.importer.ExecutionPathHelper;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.util.jar.SourceJarFinder;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.BazelBuildWithIntelliJAspectsCommand;

/**
 * Index of information extracted from aspects output for re-use during classpath computation
 * <p>
 * An instance of this class must be initialized with the output of {@link BazelBuildWithIntelliJAspectsCommand build
 * with aspects result}. This result will be used for computing classpath.
 * </p>
 */
public class JavaAspectsInfo extends JavaClasspathJarLocationResolver {

    private static Logger LOG = LoggerFactory.getLogger(JavaAspectsInfo.class);

    static boolean isJavaProtoTarget(TargetIdeInfo target) {
        return (target.getJavaIdeInfo() != null)
                && (JavaBlazeRules.getJavaProtoLibraryKinds().contains(target.getKind())
                        || target.getKind().equals(GenericBlazeRules.RuleTypes.PROTO_LIBRARY.getKind()));
    }

    final ParsedBepOutput aspectsBuildResult;
    final IntellijAspects intellijAspects;

    /** index of all aspects loaded from the build output */
    final Map<TargetKey, TargetIdeInfo> ideInfoByTargetKey;

    /** index of all jars belonging to a target */
    final Map<TargetKey, List<BlazeJarLibrary>> librariesByTargetKey;

    /** index of jars based on their root relative path, which allows lookup of jdeps entries */
    final Map<String, BlazeJarLibrary> libraryByJdepsRootRelativePath;

    public JavaAspectsInfo(ParsedBepOutput aspectsBuildResult, BazelWorkspace bazelWorkspace,
            IntellijAspects intellijAspects) throws CoreException {
        super(bazelWorkspace);
        this.aspectsBuildResult = aspectsBuildResult;
        this.intellijAspects = intellijAspects;

        // build maps
        ideInfoByTargetKey = new HashMap<>();
        librariesByTargetKey = new HashMap<>();
        libraryByJdepsRootRelativePath = new HashMap<>();

        // index all the info from each aspect
        var outputArtifacts = aspectsBuildResult
                .getOutputGroupArtifacts(OutputGroup.INFO::isPrefixOf, IntellijAspects.ASPECT_OUTPUT_FILE_PREDICATE);
        NEXT_ASPECT: for (OutputArtifact outputArtifact : outputArtifacts) {
            try {
                // parse the aspect
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Processing aspect: {}", outputArtifact);
                }
                var targetIdeInfo = TargetIdeInfo.fromProto(intellijAspects.readAspectFile(outputArtifact));
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

                // add all target produced jars to our index (so we can map them back later)
                for (var jar : javaIdeInfo.getJars()) {
                    addLibrary(new BlazeJarLibrary(jar, targetKey));
                }
                for (var jar : javaIdeInfo.getGeneratedJars()) {
                    addLibrary(new BlazeJarLibrary(jar, targetKey));
                }
                if (javaIdeInfo.getFilteredGenJar() != null) {
                    addLibrary(new BlazeJarLibrary(javaIdeInfo.getFilteredGenJar(), targetKey));
                }
                for (var jar : javaIdeInfo.getPluginProcessorJars()) {
                    addLibrary(new BlazeJarLibrary(jar, targetKey));
                }

            } catch (IOException e) {
                throw new CoreException(Status.error(format("Error reading aspect file '%s'.", outputArtifact), e));
            }
        }

        // collect runtime classpath info
        var runtimeClasspathJars =
                aspectsBuildResult.getOutputGroupArtifacts(IntellijAspects.OUTPUT_GROUP_JAVA_RUNTIME_CLASSPATH);
        var collector = new NestedSetVisitor<BlazeArtifact>(jar -> {
            if (jar instanceof LocalFileArtifact localJar) {
                var classJar = toArtifactLocation(localJar);

                var resolveOutput = locationDecoder.resolveOutput(classJar);
                if ((resolveOutput instanceof LocalFileArtifact localOutput) && !isReadable(localOutput.getPath())) {
                    LOG.error("Wrong location for runtime jar '{}'. Please report bug!", localJar);
                    return;
                }

                var jarLibrary = libraryByJdepsRootRelativePath.get(classJar.getRelativePath());
                if (jarLibrary == null) {
                    var targetLabel = readTargetLabel(localJar.getPath());
                    if (targetLabel == null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(
                                "Unable to compute target label for runtime jar '{}'. Please check if the rule producing the jar is adding the Target-Label to the jar manifest!",
                                classJar);
                        }
                        targetLabel = Label.create(format("@_unknown_jar_//:%s", classJar.getRelativePath()));
                    }

                    var builder = LibraryArtifact.builder();
                    builder.setClassJar(classJar);
                    var sourceJar = SourceJarFinder.findSourceJar(classJar, getBlazeInfo());
                    if (sourceJar != null) {
                        builder.addSourceJar(sourceJar);
                    }

                    jarLibrary = new BlazeJarLibrary(builder.build(), TargetKey.forPlainTarget(targetLabel));
                    addLibrary(jarLibrary);
                }

            }
        }, new NestedSetVisitor.VisitedState<>());
        try {
            collector.visit(runtimeClasspathJars);
        } catch (InterruptedException e) {
            throw new OperationCanceledException("interrupted");
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

    public TargetIdeInfo get(TargetKey targetKey) {
        return ideInfoByTargetKey.get(targetKey);
    }

    public List<BlazeJarLibrary> getLibraries(TargetKey targetKey) {
        return librariesByTargetKey.get(targetKey);
    }

    public BlazeJarLibrary getLibraryByJdepsRootRelativePath(String relativePath) {
        return libraryByJdepsRootRelativePath.get(relativePath);
    }

    public List<BlazeJarLibrary> getRuntimeClasspath(TargetKey targetKey) {
        if (targetKey.isPlainTarget()) {
            var outputGroupArtifacts = aspectsBuildResult
                    .getOutputGroupArtifacts(targetKey.getLabel(), IntellijAspects.OUTPUT_GROUP_JAVA_RUNTIME_CLASSPATH);
            return outputGroupArtifacts.toList()
                    .stream()
                    .filter(LocalFileOutputArtifact.class::isInstance)
                    .map(LocalFileOutputArtifact.class::cast)
                    .map(this::toArtifactLocation)
                    .map(ArtifactLocation::getRelativePath)
                    .map(this::getLibraryByJdepsRootRelativePath)
                    .filter(Predicate.not(Objects::isNull))
                    .collect(toList());
        }
        return null;
    }

    private ArtifactLocation toArtifactLocation(LocalFileArtifact localJar) {
        // check for SourceArtifact and treat specal
        if (localJar instanceof SourceArtifact sourceArtifact) {

            if (!getWorkspaceRoot().isInWorkspace(sourceArtifact.getPath())) {
                throw new IllegalArgumentException(
                        format(
                            "Invalid SourceArtifact (%s): expected absolute path pointing into workspace '%s'!",
                            sourceArtifact,
                            getWorkspaceRoot()));
            }
            return ArtifactLocation.builder()
                    .setIsSource(true)
                    .setRelativePath(getWorkspaceRoot().workspacePathFor(sourceArtifact.getPath()).relativePath())
                    .build();
        }

        // assume is in execution root

        var localJarExecutionRootRelativePath = getBlazeInfo().getExecutionRoot().relativize(localJar.getPath());
        // special case: if the execution root points to outside execution root, we route it back for ExecutionPathHelper to work
        if (localJarExecutionRootRelativePath.startsWith("../../external/")) {
            localJarExecutionRootRelativePath =
                    localJarExecutionRootRelativePath.subpath(2, localJarExecutionRootRelativePath.getNameCount());
        }

        if (localJarExecutionRootRelativePath.startsWith("../")) {
            throw new IllegalArgumentException(
                    format(
                        "Unable to handle Bazel artifact '%s' (%s). Please report bug!",
                        localJar,
                        localJarExecutionRootRelativePath));
        }

        return ExecutionPathHelper.parse(
            workspaceRoot,
            BazelBuildSystemProvider.BAZEL,
            fromPath(localJarExecutionRootRelativePath).toString());
    }
}
