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
package com.salesforce.bazel.sdk.project;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Central manager for managing BazelProject instances
 */
public abstract class BazelProjectManager {

    private final Map<String, BazelProject> projectMap = new TreeMap<>();
    private final LogHelper logger;

    public BazelProjectManager() {
        logger = LogHelper.log(this.getClass());
    }

    public void addProject(BazelProject newProject) {
        BazelProject existingProject = projectMap.get(newProject.name);
        if (existingProject != null) {
            newProject.merge(existingProject);
        }
        projectMap.put(newProject.name, newProject);
        newProject.bazelProjectManager = this;
    }

    public BazelProject getProject(String name) {
        return projectMap.get(name);
    }

    public Collection<BazelProject> getAllProjects() {
        return projectMap.values();
    }

    /**
     * Runs a build with the passed targets and returns true if no errors are returned.
     */
    public boolean isValid(BazelWorkspace bazelWorkspace, BazelCommandManager bazelCommandManager,
            BazelProject bazelProject) {
        if (bazelWorkspace == null) {
            return false;
        }

        File bazelWorkspaceRootDirectory = bazelWorkspace.getBazelWorkspaceRootDirectory();
        if (bazelWorkspaceRootDirectory == null) {
            return false;
        }

        try {
            BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                    bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

            if (bazelWorkspaceCmdRunner != null) {
                BazelProjectTargets targets = getConfiguredBazelTargets(bazelProject, false);
                List<BazelProblem> details = bazelWorkspaceCmdRunner.runBazelBuild(targets.getConfiguredTargets(),
                    Collections.emptyList(), null);
                for (BazelProblem detail : details) {
                    logger.error(detail.toString());
                }
                return details.isEmpty();

            }
        } catch (Exception anyE) {
            logger.error("Caught exception validating project [" + bazelProject.name + "]", anyE);
            // just return false below
        }
        return false;
    }

    /**
     * Locates the project that owns the source path, or null if an owning project is not found.
     * <p>
     * The sourcePath is the relative path from the root of the workspace to a source folder or source file.
     */
    public abstract BazelProject getOwningProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath);

    /**
     * Creates a project reference between this project and a set of other projects. References are used by IDE code
     * refactoring among other things. The direction of reference goes from this->updatedRefList If this project no
     * longer uses another project, removing it from the list will eliminate the project reference.
     */
    public abstract void setProjectReferences(BazelProject thisProject, List<BazelProject> updatedRefList);

    /**
     * The label that identifies the Bazel package that represents this project. This will be the 'module' label when we
     * start supporting multiple BUILD files in a single 'module'. Example: //projects/libs/foo See
     * https://github.com/salesforce/bazel-eclipse/issues/24
     */
    public abstract String getBazelLabelForProject(BazelProject bazelProject);

    /**
     * Returns a map that maps Bazel labels to their projects
     */
    public abstract Map<BazelLabel, BazelProject> getBazelLabelToProjectMap(Collection<BazelProject> bazelProjects);

    /**
     * List the Bazel targets the user has chosen to activate for this project. Each project configured for Bazel is
     * configured to track certain targets and this function fetches this list from the project preferences. After
     * initial import, this will be just the wildcard target (:*) which means all targets are activated. This is the
     * safest choice as new targets that are added to the BUILD file will implicitly get picked up. But users may choose
     * to be explicit if one or more targets in a BUILD file is not needed for development.
     * <p>
     * By contract, this method will return only one target if the there is a wildcard target, even if the user does
     * funny things in their prefs file and sets multiple targets along with the wildcard target.
     */
    public abstract BazelProjectTargets getConfiguredBazelTargets(BazelProject bazelProject,
            boolean addWildcardIfNoTargets);

    /**
     * List of Bazel build flags for this project, taken from the project configuration
     */
    public abstract List<String> getBazelBuildFlagsForProject(BazelProject bazelProject);

    /**
     * Persists preferences for the given project
     */
    public abstract void addSettingsToProject(BazelProject bazelProject, String bazelWorkspaceRoot,
            String bazelProjectPackage, List<BazelLabel> bazelTargets, List<String> bazelBuildFlags);

    /**
     * Clears all backing caches for the specified project
     */
    public void flushCaches(String projectName, BazelWorkspaceCommandRunner cmdRunner) {
        BazelProject bazelProject = getProject(projectName);
        String packageLabel = getBazelLabelForProject(bazelProject);
        cmdRunner.flushAspectInfoCacheForPackage(packageLabel);
        cmdRunner.flushQueryCache(new BazelLabel(packageLabel));
    }
}
