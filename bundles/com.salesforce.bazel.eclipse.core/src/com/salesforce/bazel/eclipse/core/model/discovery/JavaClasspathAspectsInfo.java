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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
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
public class JavaClasspathAspectsInfo extends JavaClasspathJarLocationResolver {

    private static Logger LOG = LoggerFactory.getLogger(JavaClasspathAspectsInfo.class);

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

    public JavaClasspathAspectsInfo(ParsedBepOutput aspectsBuildResult, BazelWorkspace bazelWorkspace)
            throws CoreException {
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

    /**
     * Computes the classpath based on the {@link #addTarget(BazelTarget) added targets}.
     *
     * @return the computed classpath
     * @throws CoreException
     */
    public IntellijAspects getAspects() {
        return bazelWorkspace.getParent().getModelManager().getIntellijAspects();
    }

    public List<BlazeJarLibrary> getLibraries(TargetKey targetKey) {
        return librariesByTargetKey.get(targetKey);
    }

    public BlazeJarLibrary getLibraryByJdepsRootRelativePath(String relativePath) {
        return libraryByJdepsRootRelativePath.get(relativePath);
    }
}
