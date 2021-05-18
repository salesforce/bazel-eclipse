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

import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfos;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;

/**
 * Determines in which order the Bazel Packages are imported.
 */
public class OrderProjectsFlow implements ImportFlow {

    @Override
    public String getProgressText() {
        return "Determining project order";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getProjectOrderResolver());
        Objects.requireNonNull(ctx.getBazelWorkspaceRootPackageInfo());
        Objects.requireNonNull(ctx.getSelectedBazelPackages());
        Objects.requireNonNull(ctx.getAspectTargetInfos());
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressSubMonitor) {
        ProjectOrderResolver projectOrderResolver = ctx.getProjectOrderResolver();
        BazelPackageLocation bazelWorkspaceRootPackageInfo = ctx.getBazelWorkspaceRootPackageInfo();
        List<BazelPackageLocation> selectedBazelPackages = ctx.getSelectedBazelPackages();
        AspectTargetInfos aspectTargetInfos = ctx.getAspectTargetInfos();

        Iterable<BazelPackageLocation> postOrderedModules = projectOrderResolver
                .computePackageOrder(bazelWorkspaceRootPackageInfo, selectedBazelPackages, aspectTargetInfos);

        ctx.setOrderedModules(postOrderedModules);
    }

}
