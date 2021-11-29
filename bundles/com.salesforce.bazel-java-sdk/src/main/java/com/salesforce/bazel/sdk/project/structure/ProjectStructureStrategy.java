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
 */
package com.salesforce.bazel.sdk.project.structure;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.lang.jvm.MavenProjectStructureStrategy;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Pluggable strategy for establishing the basic structure of a project. Used early during import.
 */
public abstract class ProjectStructureStrategy {
    private static final LogHelper LOG = LogHelper.log(MavenProjectStructureStrategy.class);

    // STATIC UTILITIES

    /**
     * List of pluggable strategies used to derive the source directories for a project during import. The list is
     * public and is intended to be modified by SDK extensions. Each supported language will likely have one or more
     * strategies based on conventions for the language. For example, for Java, the Maven layout (src/main/java,
     * src/test/java) is a common one and so there is a Maven strategy.
     * <p>
     * The list is processed in order, and the first strategy that recognizes the project structure wins. The last
     * strategy in the list (Bazel Query) uses a general purpose strategy that is expensive but is suitable for cases
     * where the Bazel package has a custom layout.
     */
    public static List<ProjectStructureStrategy> projectStructureStrategies = new ArrayList<>();
    static {
        // Bazel Query should be the last one in the list; it is general purpose, but expensive to run
        projectStructureStrategies.add(new BazelQueryProjectStructureStrategy());
    }

    /**
     * Only used in cases where a user running a tool with the SDK hits a problem with a custom strategy. This method
     * allows the tool user to set a config/pref option to disable the non-required strategies.
     */
    public static void toggleEnableNonrequiredStrategies(boolean newState) {
        for (ProjectStructureStrategy strategy : ProjectStructureStrategy.projectStructureStrategies) {
            if (!strategy.isRequired) {
                strategy.enabled = newState;
            }
        }
    }

    public static ProjectStructure determineProjectStructure(BazelWorkspace bazelWorkspace,
            BazelPackageLocation packageNode, BazelWorkspaceCommandRunner commandRunner) {
        ProjectStructure result = null;

        for (ProjectStructureStrategy strategy : ProjectStructureStrategy.projectStructureStrategies) {
            if (strategy.enabled) {
                result = strategy.doStructureAnalysis(bazelWorkspace, packageNode, commandRunner);
                if (result != null) {
                    LOG.info("Package {} file layout was processed by the {}",
                        packageNode.getBazelPackageFSRelativePath(), strategy.getClass().getName());
                    break;
                }
            }
        }
        return result;
    }

    // INSTANCES

    /**
     * If enabled this strategy will be used by the determineProjectStructure() static method.
     */
    public boolean enabled = true;

    /**
     * A mechanism for marking one or more strategies as required, which is normally immune to being disabled.
     * <p>
     * For example, for most cases the Bazel Query strategy should not be disabled by the user (required = true) because
     * it is the general purpose solution. Whereas the Maven strategy is an optimization, but may mistake a package
     * layout and so the user should be able to disable it (required = false).
     */
    public boolean isRequired = false;

    /**
     * Inspect the project and determine the structure of this project. If this strategy is not suited to analyze the
     * particular project, it will return null.
     *
     * @param bazelWorkspace
     *            the workspace object
     * @param packageNode
     *            the package for which to do the analysis
     * @param commandRunner
     *            the Bazel command runner in case this strategy needs to run ad-hoc Bazel commands to do the analysis
     */
    public abstract ProjectStructure doStructureAnalysis(BazelWorkspace bazelWorkspace,
            BazelPackageLocation packageNode, BazelWorkspaceCommandRunner commandRunner);
}
