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
package com.salesforce.bazel.eclipse.projectimport.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfos;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Computes all aspects for all bazel packages being imported.
 * <p>
 * This step in the flow can take a <b>LONG</b> time if the Bazel workspace is dirty and needs to be rebuilt. If users
 * complain about the slowness of this step, remind them to run a <i>bazel build //...</i> prior to import.
 */
public class LoadAspectsFlow implements ImportFlow {
    private static final LogHelper LOG = LogHelper.log(LoadAspectsFlow.class);

    @Override
    public String getProgressText() {
        return "Loading type information";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getSelectedBazelPackages());
        Objects.requireNonNull(ctx.getPackageLocationToTargets());
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressMonitor) {
        BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        List<String> labels = new ArrayList<>();
        Map<BazelPackageLocation, List<BazelLabel>> map = ctx.getPackageLocationToTargets();
        for (BazelPackageLocation packageLocation : ctx.getSelectedBazelPackages()) {
            List<BazelLabel> targets = map.get(packageLocation);
            if (targets == null) {
                LOG.warn("There are no targets configured for package {}, will not run any aspects for it.",
                    packageLocation.getBazelPackageFSRelativePath());
                continue;
            }
            for (BazelLabel target : targets) {
                labels.add(target.getLabelPath());
            }
        }

        // run the aspect for specified targets and get an AspectTargetInfo for each
        try {
            Map<BazelLabel, Set<AspectTargetInfo>> targetInfos =
                    bazelWorkspaceCmdRunner.getAspectTargetInfos(labels, "importWorkspace");
            List<AspectTargetInfo> allTargetInfos = new ArrayList<>();
            for (Set<AspectTargetInfo> targetTargetInfos : targetInfos.values()) {
                allTargetInfos.addAll(targetTargetInfos);
            }
            ctx.setAspectTargetInfos(new AspectTargetInfos(allTargetInfos));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
