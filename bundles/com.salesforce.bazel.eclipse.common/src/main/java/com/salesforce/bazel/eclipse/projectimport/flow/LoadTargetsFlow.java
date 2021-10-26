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
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.eclipse.projectimport.flow;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

/**
 * Runs bazel query for all targets to warmup the query cache.
 *
 * This flow loads all targets with a single "bazel query" invocation.
 */
public class LoadTargetsFlow extends AbstractImportFlowStep {

    public LoadTargetsFlow(BazelCommandManager commandManager, BazelProjectManager projectManager,
            ResourceHelper resourceHelper) {
        super(commandManager, projectManager, resourceHelper);
    }

    @Override
    public String getProgressText() {
        return "Extracting available Bazel targets for the chosen Bazel packages.";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getPackageLocationToTargets());
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressSubMonitor) throws Exception {
        final BazelWorkspace bazelWorkspace = Objects.requireNonNull(getBazelWorkspace());
        final BazelCommandManager cmdMgr = getCommandManager();
        BazelWorkspaceCommandRunner cmdRunner = cmdMgr.getWorkspaceCommandRunner(bazelWorkspace);
        Collection<BazelLabel> allTargets = new HashSet<>();
        Map<BazelPackageLocation, List<BazelLabel>> packageLocationToTargets = ctx.getPackageLocationToTargets();
        for (BazelPackageLocation packageLocation : packageLocationToTargets.keySet()) {
            List<BazelLabel> targets = packageLocationToTargets.get(packageLocation);
            if (!targets.isEmpty()) {
                // flush the cache for each package because we don't know whether the previously loaded targets
                // match or not (we could do better - actually check which targets were loaded?)
                cmdRunner.flushQueryCache(targets.get(0));
                // collect the targets
                allTargets.addAll(targets);
            }
        }
        // run bazel query once, for all targets - the results are cached
        cmdRunner.queryBazelTargetsInBuildFile(allTargets);
    }

}
