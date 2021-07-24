/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.aspect;

import java.util.HashMap;
import java.util.List;

import com.salesforce.bazel.sdk.graph.BazelDependencyGraph;
import com.salesforce.bazel.sdk.graph.BazelDependencyGraphFactory;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Factory that uses the set of aspect infos generated for a workspace to construct the dependency graph.
 */
public class AspectDependencyGraphFactory {

    /**
     * Builds the dependency graph using the data collected by running aspects. It is typical that the list of aspects
     * covers all packages in the Workspace, but for some use cases it may be possible to use a subset of packages.
     * <p>
     * Passing includeTarget=true will increase the complexity of the graph. It will track dependencies per target in a
     * package. Generally, this is more data than applications need. If includeTarget=false then the graph will have an
     * edge in between two packages if any target in package A depends on any target in package B.
     */
    public static BazelDependencyGraph build(AspectTargetInfos aspects, boolean includeTarget) {
        BazelDependencyGraph graph = BazelDependencyGraphFactory.build("AspectDependencyGraphFactory", new HashMap<>());

        // TODO the stripTargetFromLabel invocations here need to be removed in order for us to solve the
        // the cyclical dependency problems tracked by https://github.com/salesforce/bazel-java-sdk/issues/23
        // the InMemoryDependencyGraph will also need to be updated to support target level edges

        for (AspectTargetInfo info : aspects.getTargetInfos()) {
            String sourcePackagePath = info.getLabelPath();
            if (!includeTarget) {
                sourcePackagePath = stripTargetFromLabel(sourcePackagePath);
            }
            List<String> depLabels = info.getDeps();
            for (String depLabel : depLabels) {
                if (!includeTarget) {
                    depLabel = stripTargetFromLabel(depLabel);
                }

                if (sourcePackagePath.equals(depLabel)) {
                    // this is a intra-package dependency (a common case when targets are stripped)
                    continue;
                }

                graph.addDependency(sourcePackagePath, depLabel);
            }
        }
        return graph;
    }

    private static String stripTargetFromLabel(String labelStr) {
        BazelLabel label = new BazelLabel(labelStr);
        if (label.isExternalRepoLabel()) {
            // this is an external workspace ref, we do not change these since they are correct as-is
            // ex:  @maven//:junit_junit
            return label.getLabelPath();
        }
        return label.getPackagePath(true);
    }

}
