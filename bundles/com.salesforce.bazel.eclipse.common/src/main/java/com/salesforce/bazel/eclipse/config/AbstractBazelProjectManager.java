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
package com.salesforce.bazel.eclipse.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.utils.BazelProjectSettingsUtils;
import com.salesforce.bazel.eclipse.utils.EclipseProjectSettingsUtils;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;

public abstract class AbstractBazelProjectManager extends BazelProjectManager {

    public AbstractBazelProjectManager() {}

    @Override
    public BazelProject getOwningProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath) {
        return EclipseProjectSettingsUtils.getOwningProjectForSourcePath(bazelWorkspace, sourcePath, getAllProjects(),
            getResourceHelper(), getJavaCoreHelper());
    }

    /**
     * The label that identifies the Bazel package that represents this Eclipse project. This will be the 'module' label
     * when we start supporting multiple BUILD files in a single 'module'. Example: //projects/libs/foo See
     * https://github.com/salesforce/bazel-eclipse/issues/24
     */
    @Override
    public String getBazelLabelForProject(BazelProject bazelProject) {
        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = getResourceHelper().getProjectBazelPreferences(eclipseProject);
        return eclipseProjectBazelPrefs.get(IEclipseBazelProjectSettings.PROJECT_PACKAGE_LABEL, null);
    }

    /**
     * Returns a map that maps Bazel labels to their Eclipse projects
     */
    @Override
    public Map<BazelLabel, BazelProject> getBazelLabelToProjectMap(Collection<BazelProject> bazelProjects) {
        Map<BazelLabel, BazelProject> labelToProject = new HashMap<>();
        for (BazelProject bazelProject : bazelProjects) {
            BazelProjectTargets activatedTargets = getConfiguredBazelTargets(bazelProject, false);
            List<BazelLabel> labels =
                    activatedTargets.getConfiguredTargets().stream().map(BazelLabel::new).collect(Collectors.toList());
            for (BazelLabel label : labels) {
                labelToProject.merge(label, bazelProject, (k1, k2) -> {
                    throw new IllegalStateException("Duplicate label: " + label + " - this is bug");
                });
            }
        }
        return labelToProject;
    }

    @Override
    public BazelProjectTargets getConfiguredBazelTargets(BazelProject bazelProject, boolean addWildcardIfNoTargets) {
        return BazelProjectSettingsUtils.getConfiguredBazelTargets(getResourceHelper(), bazelProject,
            addWildcardIfNoTargets);
    }

    @Override
    public void addSettingsToProject(BazelProject bazelProject, String bazelWorkspaceRoot, String packageFSPath,
            List<BazelLabel> bazelTargets, List<String> bazelBuildFlags) {

        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = getResourceHelper().getProjectBazelPreferences(eclipseProject);

        eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY,
            bazelWorkspaceRoot);

        // convert file system path to bazel path; for linux/macos the slashes are already fine,
        // this is only a thing for windows
        String bazelPackagePath = packageFSPath.replace(FSPathHelper.WINDOWS_BACKSLASH, "/");

        if (!bazelPackagePath.startsWith("//")) {
            bazelPackagePath = "//" + bazelPackagePath;
        }
        eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.PROJECT_PACKAGE_LABEL, bazelPackagePath);

        for (int i = 0; i < bazelTargets.size(); i++) {
            eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.TARGET_PROPERTY_PREFIX + i,
                bazelTargets.get(i).getLabelPath());
        }
        for (int i = 0; i < bazelBuildFlags.size(); i++) {
            eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.BUILDFLAG_PROPERTY_PREFIX + i,
                bazelBuildFlags.get(i));
        }
        try {
            eclipseProjectBazelPrefs.flush();
        } catch (Exception anyE) {
            throw new RuntimeException(anyE);
        }
    }

    /**
     * List of Bazel build flags for this Eclipse project, taken from the project configuration
     */
    @Override
    public List<String> getBazelBuildFlagsForProject(BazelProject bazelProject) {
        return BazelProjectSettingsUtils.getBazelBuildFlagsForProject(getResourceHelper(), bazelProject);
    }

    @Override
    public void setProjectReferences(BazelProject thisProject, List<BazelProject> updatedRefList) {
        EclipseProjectSettingsUtils.setProjectReferences(getResourceHelper(), thisProject, updatedRefList);
    }

    protected abstract ResourceHelper getResourceHelper();

    protected abstract JavaCoreHelper getJavaCoreHelper();
}
