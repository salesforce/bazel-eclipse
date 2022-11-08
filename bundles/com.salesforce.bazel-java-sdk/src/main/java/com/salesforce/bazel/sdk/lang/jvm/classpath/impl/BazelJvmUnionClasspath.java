/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.sdk.lang.jvm.classpath.impl;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspath;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathResponse;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelBuildFile;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Computes a JVM classpath for a BazelProject based on the dependencies configured in Bazel.
 * <p>
 * This classpath implementation collapses the dependencies for all Java rules in the package into a single classpath,
 * otherwise known as a <b>union</b> classpath. The entries are marked as 'main', 'runtime' or 'test' entries. 
 * <p>
 * This may not be precise enough for all use cases but for most tooling (e.g. IDEs) use cases this is sufficient granularity. 
 * <p>
 * Targets within the package are excluded from the classpath, as they are presumed to be represented by source code found in
 * source folders. Dependencies on other BazelProjects are represented as BazelProject references in the response.
 * <p>
 * There is an instance of this class for each project.
 */
public class BazelJvmUnionClasspath implements JvmClasspath {
    // TODO make classpath cache timeout configurable
    private static final long CLASSPATH_CACHE_TIMEOUT_MS = 300000;

    protected final BazelWorkspace bazelWorkspace;
    protected final BazelProjectManager bazelProjectManager;
    protected final BazelProject bazelProject;
    protected final ImplicitClasspathHelper implicitDependencyHelper;
    protected final OperatingEnvironmentDetectionStrategy osDetector;
    protected final BazelCommandManager bazelCommandManager;
    protected final List<BazelJvmClasspathStrategy> orderedClasspathStrategies;
    private final LogHelper logger;
    
    private JvmClasspathResponse cachedEntries;
    private long cachePutTimeMillis = 0;

    public BazelJvmUnionClasspath(BazelWorkspace bazelWorkspace, BazelProjectManager bazelProjectManager,
            BazelProject bazelProject, ImplicitClasspathHelper implicitDependencyHelper,
            OperatingEnvironmentDetectionStrategy osDetector, BazelCommandManager bazelCommandManager,
            List<BazelJvmClasspathStrategy> orderedClasspathStrategies) {
        this.bazelWorkspace = bazelWorkspace;
        this.bazelProjectManager = bazelProjectManager;
        this.bazelProject = bazelProject;
        this.implicitDependencyHelper = implicitDependencyHelper;
        this.osDetector = osDetector;
        this.bazelCommandManager = bazelCommandManager;
        this.orderedClasspathStrategies = orderedClasspathStrategies;

        logger = LogHelper.log(this.getClass());
    }

    @Override
    public void clean() {
        cachedEntries = null;
        cachePutTimeMillis = 0;
    }

    /**
     * Computes the JVM classpath for the associated BazelProject
     */
    @Override
    public JvmClasspathResponse getClasspathEntries() {
        // sanity check
        if (bazelWorkspace == null) {
            // not sure how we could get here, but just check
            throw new IllegalStateException(
                    "Attempt to retrieve the classpath of a Bazel Java project prior to setting up the Bazel workspace.");
        }
        long startTimeMS = System.currentTimeMillis();

        boolean isImport = false;
        JvmClasspathResponse response = getCachedEntries();
        if (response != null) {
            return response;
        }
        response = new JvmClasspathResponse();

        logger.info("Computing classpath for project " + bazelProject.name + " (import? " + isImport + ")");
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        BazelProjectTargets configuredTargetsForProject = null;
        BazelBuildFile bazelBuildFileModel = null;

        // get the model of the BUILD file for this package, which will tell us the type of each target, and the list
        // of all targets if configured with the wildcard target
        try {
            configuredTargetsForProject = bazelProjectManager.getConfiguredBazelTargets(bazelProject, false);
            bazelBuildFileModel = getBuildFile(bazelWorkspaceCmdRunner, configuredTargetsForProject);
        } catch (Exception anyE) {
            logger.error("Unable to compute classpath containers entries for project " + bazelProject.name, anyE);
            return returnEmptyClasspathOrThrow(anyE);
        }
            
        // now get the actual list of activated targets, with wildcard resolved using the BUILD file model if necessary
        Set<String> actualActivatedTargets = configuredTargetsForProject.getActualTargets(bazelBuildFileModel);

        // Iterate over the activated targets and compute the classpath for each. The result will be aggregated into a
        // union classpath in the JvmClasspathResponse that contains all entries for all activated targets
        for (String targetLabel : actualActivatedTargets) {
            String targetType = bazelBuildFileModel.getRuleTypeForTarget(targetLabel);
            
            try {
                // invoke our classpath strategies in order, until one is able to complete the classpath
                for (BazelJvmClasspathStrategy strategy : orderedClasspathStrategies) {
                    
                    strategy.getClasspathForTarget(bazelProject, targetLabel, targetType, configuredTargetsForProject, 
                        actualActivatedTargets, response);
                    if (response.isComplete) {
                        break;
                    }
                    
                }
            } catch (Exception anyE) {
                // computing the classpath for a single target can fail, and we will try to continue
                logger.error("Exception caught during classpath computation: {}", anyE, anyE.getMessage());
                continue;
            }
        } // for loop

        // cache the entries
        cachePutTimeMillis = System.currentTimeMillis();
        cachedEntries = response;
        logger.debug("Cached the classpath for project " + bazelProject.name);

        SimplePerfRecorder.addTime("classpath", startTimeMS);

        return cachedEntries;
    }


    // INTERNAL
    
    private JvmClasspathResponse getCachedEntries() {
        if (cachedEntries != null) {
            long now = System.currentTimeMillis();
            if ((now - cachePutTimeMillis) <= CLASSPATH_CACHE_TIMEOUT_MS) {
                logger.debug("  Using cached classpath for project " + bazelProject.name);
                return cachedEntries;
            }
            logger.info("Evicted classpath from cache for project " + bazelProject.name);
            cachedEntries = null;
        }
        return null;
    }
    
    private BazelBuildFile getBuildFile(BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner, BazelProjectTargets configuredTargetsForProject) throws Exception {
        // TODO this code is hard to follow, why are there collections where we expect there to be a single build file?
        
        BazelBuildFile bazelBuildFileModel = null;

        // we pass the targets that are configured for the current project to bazel query
        // typically, this is a single wildcard target, but the user may
        // also have specified explicit targets to use
        List<BazelLabel> labels = configuredTargetsForProject.getConfiguredTargets().stream()
                .map(BazelLabel::new).collect(Collectors.toList());
        Collection<BazelBuildFile> buildFiles = bazelWorkspaceCmdRunner.queryBazelTargetsInBuildFile(labels);
        // since we only call query with labels for the same package, we expect to get a single BazelBuildFile instance back
        if (buildFiles.isEmpty()) {
            throw new IllegalStateException("Unexpected empty BazelBuildFile collection, this is a bug");
        }
        if (buildFiles.size() > 1) {
            throw new IllegalStateException("Expected a single BazelBuildFile instance, this is a bug");
        }
        bazelBuildFileModel = buildFiles.iterator().next();
        
        return bazelBuildFileModel;
    }

    private void continueOrThrow(Throwable th) {
        // under real usage, we suppress fatal exceptions because sometimes there are IDE timing issues that can
        // be corrected if the classpath is computed again.
        // But under tests, we want to fail fatally otherwise tests could pass when they shouldn't
        if (osDetector.isTestRuntime()) {
            throw new IllegalStateException("The classpath could not be computed by the BazelClasspathContainer", th);
        }
    }

    private JvmClasspathResponse returnEmptyClasspathOrThrow(Throwable th) {
        continueOrThrow(th);
        return new JvmClasspathResponse();
    }

}
