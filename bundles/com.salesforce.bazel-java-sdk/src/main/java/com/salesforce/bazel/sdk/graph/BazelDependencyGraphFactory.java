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
package com.salesforce.bazel.sdk.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BazelDependencyGraphFactory {

    /**
     * List of configured builders. Builders earlier in the list will take precedence over later builders.
     * <p>
     * If you create a new implementation of BazelDependencyGraph, add a builder for it at the head of this list so that
     * your implementation will be used instead.
     */
    public static List<BazelDependencyGraphBuilder> builders = new ArrayList<>();
    static {
        // unless the user configures a custom graph impl, the default inmemory graph will be built
        builders.add(new InMemoryDependencyGraphBuilder());
    }

    /**
     * Finds a builder that can build a dependency graph, and builds it. After the initial build, the caller will need
     * to fill in the graph using the addDependency() method for every edge in the graph.
     * <p>
     * The caller parameter is intended to include the caller name (e.g. AspectDependencyGraphFactory) which may be used
     * to determine what type of graph is built, and/or for logging purposes. The options map is implementation specific
     * - the graphs that are configured will publish options that may be available.
     */
    public static BazelDependencyGraph build(String caller, Map<String, String> options) {
        for (BazelDependencyGraphBuilder builder : builders) {
            BazelDependencyGraph graph = builder.build(caller, options);
            if (graph != null) {
                return graph;
            }
        }
        return null;
    }

}
