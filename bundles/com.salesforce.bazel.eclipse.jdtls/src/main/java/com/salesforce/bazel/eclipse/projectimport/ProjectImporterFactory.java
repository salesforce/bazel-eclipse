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
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.salesforce.bazel.eclipse.projectimport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.projectimport.flow.BjlsFlowProjectImporter;
import com.salesforce.bazel.eclipse.projectimport.flow.BjlsSetupClasspathContainersFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.CreateProjectsFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.CreateRootProjectFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.DetermineTargetsFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.ImportFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.InitImportFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.InitJREFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.LoadAspectsFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.LoadTargetsFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.OrderProjectsFlow;
import com.salesforce.bazel.eclipse.projectimport.flow.SetupProjectBuildersFlow;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.utils.BazelCompilerUtils;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolverImpl;

public class ProjectImporterFactory {

    // signals that we are in a delicate bootstrapping operation
    public static AtomicBoolean importInProgress = new AtomicBoolean(false);

    private static List<ImportFlow> createFlows() {
        // each project import uses a new list of flow instances so that flows can have state
        // the List returned here needs to be modifiable
        var bazelProjectManager = ComponentContext.getInstance().getProjectManager();
        var resourceHelper = ComponentContext.getInstance().getResourceHelper();
        var bazelCommandManager = ComponentContext.getInstance().getBazelCommandManager();
        return new ArrayList<>(Arrays.asList(new InitJREFlow(),
            new InitImportFlow(bazelCommandManager, bazelProjectManager, resourceHelper),
            new DetermineTargetsFlow(bazelCommandManager, bazelProjectManager, resourceHelper),
            new LoadAspectsFlow(bazelCommandManager, bazelProjectManager, resourceHelper),
            new LoadTargetsFlow(bazelCommandManager, bazelProjectManager, resourceHelper),
            new CreateRootProjectFlow(bazelCommandManager, bazelProjectManager, resourceHelper),
            new OrderProjectsFlow(), new CreateProjectsFlow(bazelCommandManager, bazelProjectManager, resourceHelper),
            new SetupProjectBuildersFlow(), new BjlsSetupClasspathContainersFlow(bazelCommandManager,
                    bazelProjectManager, resourceHelper, ComponentContext.getInstance().getJavaCoreHelper())));
    }

    private final BazelPackageLocation bazelWorkspaceRootPackageInfo;
    private final List<BazelPackageLocation> selectedBazelPackages;
    private final List<ImportFlow> flows;

    private ProjectOrderResolver projectOrderResolver = new ProjectOrderResolverImpl();

    public ProjectImporterFactory(BazelPackageLocation bazelWorkspaceRootPackageInfo,
            List<BazelPackageLocation> selectedBazelPackages) {
        this(bazelWorkspaceRootPackageInfo, selectedBazelPackages, createFlows());
    }

    ProjectImporterFactory(BazelPackageLocation bazelWorkspaceRootPackageInfo,
            List<BazelPackageLocation> selectedBazelPackages, List<ImportFlow> flows) {
        this.bazelWorkspaceRootPackageInfo = Objects.requireNonNull(bazelWorkspaceRootPackageInfo);
        this.selectedBazelPackages = Objects.requireNonNull(selectedBazelPackages);
        this.flows = Objects.requireNonNull(flows);
    }

    public ProjectImporter build() {
        return new BjlsFlowProjectImporter(flows.toArray(new ImportFlow[flows.size()]), bazelWorkspaceRootPackageInfo,
                selectedBazelPackages, projectOrderResolver, BazelCompilerUtils.getOSBazelPath(), importInProgress);
    }

    public void setImportOrderResolver(ProjectOrderResolver projectOrderResolver) {
        this.projectOrderResolver = projectOrderResolver;
    }

    public void skipJREWarmup() {
        flows.removeIf(flow -> flow.getClass() == InitJREFlow.class);
    }

    public void skipQueryCacheWarmup() {
        flows.removeIf(flow -> flow.getClass() == LoadTargetsFlow.class);
    }
}
