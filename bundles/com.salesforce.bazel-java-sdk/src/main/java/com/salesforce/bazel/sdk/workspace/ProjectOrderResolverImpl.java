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
package com.salesforce.bazel.sdk.workspace;

import java.lang.invoke.MethodHandles;
import java.util.List;

import com.salesforce.bazel.sdk.aspect.AspectDependencyGraphFactory;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfos;
import com.salesforce.bazel.sdk.graph.BazelDependencyGraph;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Orders modules for import such that upstream dependencies are imported before downstream dependencies.
 */
public class ProjectOrderResolverImpl implements ProjectOrderResolver {
    private static final LogHelper LOG = LogHelper.log(MethodHandles.lookup().lookupClass());

    public ProjectOrderResolverImpl() {}

    /**
     * Orders all of the packages for import such that no package is imported before any of modules that it depends on.
     * <p>
     * Given the complex nature of the dependency graph, and the user can select an arbitrary set of packages to import,
     * this . It assumes that there are hundreds/thousands of packages in the Bazel workspace, and the user will pick
     * 10-20 to import.
     *
     * @return ordered list of modules - leaves nodes goes first, those which dependent on them next and so on up to the
     *         root module
     */
    @Override
    public Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            AspectTargetInfos aspects) {
        List<BazelPackageLocation> selectedPackages = rootPackage.gatherChildren();

        return computePackageOrder(rootPackage, selectedPackages, aspects);
    }

    /**
     * Orders the packages selected for import such that no package is imported before any of modules that it depends
     * on.
     * <p>
     * Given the complex nature of the dependency graph, and the user can select an arbitrary set of packages to import,
     * this . It assumes that there are hundreds/thousands of packages in the Bazel workspace, and the user will pick
     * 10-20 to import.
     *
     * @return ordered list of modules - leaves nodes goes first, those which dependent on them next and so on up to the
     *         root module
     */
    @Override
    public Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            List<BazelPackageLocation> selectedPackages, AspectTargetInfos aspects) {

        if (aspects == null) {
            return selectedPackages;
        }

        // first, generate the dependency graph for the entire workspace
        List<BazelPackageLocation> orderedModules = null;
        try {
            BazelDependencyGraph workspaceDepGraph = AspectDependencyGraphFactory.build(aspects, false);
            boolean followExternalTransitives = false;
            orderedModules = workspaceDepGraph.orderLabels(selectedPackages, followExternalTransitives);

            StringBuffer sb = new StringBuffer();
            sb.append("ImportOrderResolver order of modules: ");
            for (BazelPackageLocation pkg : orderedModules) {
                sb.append(pkg.getBazelPackageName());
                sb.append("  ");
            }
            LOG.debug(sb.toString());
        } catch (Exception anyE) {
            LOG.error("error computing package order", anyE);
            orderedModules = selectedPackages;
        }
        return orderedModules;

    }
}