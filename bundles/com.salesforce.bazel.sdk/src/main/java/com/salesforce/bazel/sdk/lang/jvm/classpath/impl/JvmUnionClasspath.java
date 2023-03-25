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

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy.JvmClasspathAspectStrategy;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy.JvmClasspathStrategy;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy.JvmClasspathStrategyRequest;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.util.ImplicitClasspathHelper;
import com.salesforce.bazel.sdk.model.BazelTargetKind;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectOld;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

/**
 * Computes a JVM classpath for a BazelProjectOld based on the dependencies configured in Bazel.
 * <p>
 * This classpath implementation collapses the dependencies for all Java rules in the package into a single classpath,
 * otherwise known as a <b>union</b> classpath. The entries are marked as 'main', 'runtime' or 'test' entries.
 * <p>
 * This may not be precise enough for all use cases but for most tooling (e.g. IDEs) use cases this is sufficient
 * granularity.
 * <p>
 * Targets within the package are excluded from the classpath, as they are presumed to be represented by source code
 * found in source folders. Dependencies on other BazelProjects are represented as BazelProjectOld references in the
 * response.
 * <p>
 * There is an instance of this class for each project.
 */
public class JvmUnionClasspath extends JvmInMemoryClasspath {
    private static Logger LOG = LoggerFactory.getLogger(JvmUnionClasspath.class);

    public static JvmUnionClasspath withAspectStrategy(BazelProjectOld bazelProject, BazelWorkspace bazelWorkspace,
            BazelCommandManager bazelCommandManager) {
        var implicitDependencyHelper = new ImplicitClasspathHelper();
        var osDetector = new RealOperatingEnvironmentDetectionStrategy();
        return new JvmUnionClasspath(bazelWorkspace, bazelProject.bazelProjectManager, bazelProject,
                implicitDependencyHelper, osDetector, bazelCommandManager,
                List.of(new JvmClasspathAspectStrategy(bazelWorkspace, bazelProject.bazelProjectManager,
                        implicitDependencyHelper, osDetector, bazelCommandManager)));
    }

    protected final BazelWorkspace bazelWorkspace;
    protected final BazelProjectManager bazelProjectManager;
    protected final BazelProjectOld bazelProject;
    protected final ImplicitClasspathHelper implicitDependencyHelper;
    protected final OperatingEnvironmentDetectionStrategy osDetector;
    protected final BazelCommandManager bazelCommandManager;

    protected final List<JvmClasspathStrategy> orderedClasspathStrategies;

    public JvmUnionClasspath(BazelWorkspace bazelWorkspace, BazelProjectManager bazelProjectManager,
            BazelProjectOld bazelProject, ImplicitClasspathHelper implicitDependencyHelper,
            OperatingEnvironmentDetectionStrategy osDetector, BazelCommandManager bazelCommandManager,
            List<JvmClasspathStrategy> orderedClasspathStrategies) {
        super(bazelProject.name);

        this.bazelWorkspace = bazelWorkspace;
        this.bazelProjectManager = bazelProjectManager;
        this.bazelProject = bazelProject;
        this.implicitDependencyHelper = implicitDependencyHelper;
        this.osDetector = osDetector;
        this.bazelCommandManager = bazelCommandManager;
        this.orderedClasspathStrategies = orderedClasspathStrategies;
    }

    /**
     * Computes the JVM classpath for the associated BazelProjectOld. This response is cached in the super class.
     * External callers will invoke getClasspathEntries() which in turn invokes this method.
     *
     * @throws Exception
     */
    @Override
    protected JvmClasspathData computeClasspath(WorkProgressMonitor progressMonitor) throws Exception {
        // sanity check
        if (bazelWorkspace == null) {
            // not sure how we could get here, but just check
            throw new IllegalStateException(
                    "Attempt to retrieve the classpath of a Bazel Java project prior to setting up the Bazel workspace.");
        }
        var startTimeMS = System.currentTimeMillis();

        var isImport = false;
        var response = new JvmClasspathData();

        LOG.debug("Computing classpath for project " + bazelProject.name + " (import? " + isImport + ")");
        var bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        

        // get the model of the BUILD file for this package, which will tell us the type of each target, and the list
        // of all targets if configured with the wildcard target
        var configuredTargetsForProject = bazelProjectManager.getConfiguredBazelTargets(bazelProject, false);
        BazelBuildFile bazelBuildFileModel = getBuildFile(bazelWorkspaceCmdRunner, configuredTargetsForProject);

        // now get the actual list of activated targets, with wildcard resolved using the BUILD file model if necessary
        Set<String> actualActivatedTargets = configuredTargetsForProject.getActualTargets(bazelBuildFileModel);

        // Iterate over the activated targets and compute the classpath for each. The result will be aggregated into a
        // union classpath in the JvmClasspathResponse that contains all entries for all activated targets
        for (String targetLabel : actualActivatedTargets) {
            String targetKindString = bazelBuildFileModel.getRuleTypeForTarget(targetLabel);
            var targetKind = BazelTargetKind.valueOfIgnoresCase(targetKindString);

            // skip targets that should not be processed during classpath computation
            if (!targetKind.isRegistered()) {
                continue;
            }

            // invoke our classpath strategies in order, until one is able to complete the classpath
            for (JvmClasspathStrategy strategy : orderedClasspathStrategies) {
                var request = new JvmClasspathStrategyRequest(bazelProject, targetLabel,
                        targetKind, configuredTargetsForProject, actualActivatedTargets, response);
                strategy.getClasspathForTarget(request);
                if (response.isComplete) {
                    break;
                }

            }
        } // for loop

        // cache the entries
        cachePutTimeMillis = System.currentTimeMillis();
        cachedClasspath = response;
        LOG.debug("Cached the classpath for project " + bazelProject.name);

        SimplePerfRecorder.addTime("classpath", startTimeMS);

        return cachedClasspath;
    }

}
