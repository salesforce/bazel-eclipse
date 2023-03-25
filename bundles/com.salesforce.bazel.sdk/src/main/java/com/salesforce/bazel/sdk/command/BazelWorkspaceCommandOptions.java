/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Holds the list of command options defined for the Workspace. Command options come from .bazelrc file(s) (and
 * additional files imported therein) found in the standard file system locations.
 * <p>
 * https://docs.bazel.build/versions/2.0.0/guide.html#bazelrc
 * <p>
 * To see the list of options used for your workspace, run this command: bazel test --announce_rc It probably seems odd
 * to use 'test' as the verb, but that provides the most visibility into the set options.
 * <p>
 * This gets populated at runtime by BazelWorkspaceMetadataStrategy.populateBazelWorkspaceCommandOptions()
 */
public class BazelWorkspaceCommandOptions {

    private final BazelWorkspace bazelWorkspace;
    private final Map<String, String> allExplicitOptions = new HashMap<>();
    private final Map<String, Map<String, String>> contextualExplicitOptions = new HashMap<>();

    public BazelWorkspaceCommandOptions(BazelWorkspace bazelWorkspace) {
        this.bazelWorkspace = bazelWorkspace;
    }

    private Map<String, String> getContextualMap(String context) {
        var contextualMap = contextualExplicitOptions.get(context);
        if (contextualMap == null) {
            contextualMap = new TreeMap<>();
            contextualExplicitOptions.put(context, contextualMap);
        }
        return contextualMap;
    }

    /**
     * Contextual options are indexed based on their source context (e.g. test, build) This method does not follow
     * Inherited conventions.
     */
    public String getContextualOption(String context, String optionName) {
        var contextualMap = contextualExplicitOptions.get(context);
        if (contextualMap != null) {
            return contextualMap.get(optionName);
        }
        return null;
    }

    /**
     * Contextual options are indexed based on their source context (e.g. test, build) This method does not follow
     * Inherited conventions.
     */
    public String getContextualOptionWithDefault(String context, String optionName) {
        var optionValue = getContextualOption(context, optionName);

        if (optionValue == null) {
            // apply the default TODO build the dictionary of default values for options
        }

        return optionValue;
    }

    /**
     * Get the command option, regardless of context (test, build)
     */
    public String getOption(String optionName) {
        return allExplicitOptions.get(optionName);
    }

    /**
     * Get the command option, regardless of context (test, build)
     */
    public String getOptionWithDefault(String optionName) {
        var optionValue = allExplicitOptions.get(optionName);

        if (optionValue == null) {
            // apply the default TODO build the dictionary of default values for options
        }

        return optionValue;
    }

    /**
     * Method to parse options from a 'bazel test' command run with the --announce_rc option
     */
    public void parseOptionsFromOutput(List<String> outputLines) {
        // SAMPLE OUTPUT
        // INFO: Options provided by the client:
        //   Inherited 'common' options: --isatty=1 --terminal_columns=260
        // INFO: Reading rc options for 'test' from /Users/darth/dev/deathstar/.user-bazelrc:
        //   Inherited 'build' options: --javacopt=-source 8 -target 8 --host_javabase=//tools/jdk:my-linux-jdk11 --javabase=//tools/jdk:my-linux-jdk8 --stamp
        //      --workspace_status_command tools/buildstamp/get_workspace_status --test_output=errors --verbose_explanations --explain=/tmp/bazel_explain.txt
        //      --output_filter=^(?!@) --incompatible_no_support_tools_in_action_inputs=false --incompatible_depset_is_not_iterable=false --incompatible_new_actions_api=false
        //      --incompatible_disable_deprecated_attr_params=false --incompatible_depset_union=false
        // INFO: Reading rc options for 'test' from /Users/darth/dev/deathstar/.base-bazelrc:
        //   'test' options: --explicit_java_test_deps=true --test_timeout=45,180,300,360 --test_tag_filters=-flaky

        for (String line : outputLines) {
            line = line.trim();

            var optionsIndex = line.indexOf("options:");
            if (optionsIndex == -1) {
                // this line has nothing to do with options, ignore
                continue;
            }

            // some lines are prefixed with "Inherited", remove that
            var inheritedIndex = line.indexOf("Inherited");
            if (inheritedIndex != -1) {
                line = line.substring(inheritedIndex + 10);
                line = line.trim();
                optionsIndex = line.indexOf("options:");
            }

            // pick apart the context name from the sequence of options
            var optionsContext = line.substring(1, optionsIndex - 2); // 'build' options: ...         => build
            var optionsFullString = line.substring(optionsIndex + 9); // 'common' options: --isatty=1 => --isatty=1

            var optionsSplitList = optionsFullString.split(" --");
            for (String option : optionsSplitList) {
                if (option.startsWith("--")) {
                    // first option will still have it
                    option = option.substring(2);
                }
                var optionTokens = option.split("=");
                if (optionTokens.length > 2) {
                    System.err.println("Did not know how to parse option [" + option + "] from line " + line);
                }
                if (optionTokens.length == 1) {
                    // if only the option name is provided, the value is implied to be 'true' (e.g. --stamp is interpreted as --stamp=true)
                    allExplicitOptions.put(optionTokens[0], "true");
                    getContextualMap(optionsContext).put(optionTokens[0], "true");
                } else {
                    allExplicitOptions.put(optionTokens[0], optionTokens[1]);
                    getContextualMap(optionsContext).put(optionTokens[0], optionTokens[1]);
                }
            }
        }
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("BazelWorkspaceCommandOptions: workspace_name:");
        sb.append(bazelWorkspace.getName());
        for (String optionName : allExplicitOptions.keySet()) {
            sb.append(" [");
            sb.append(optionName);
            sb.append(":");
            sb.append(allExplicitOptions.get(optionName));
            sb.append("]");
        }

        return sb.toString();
    }
}
