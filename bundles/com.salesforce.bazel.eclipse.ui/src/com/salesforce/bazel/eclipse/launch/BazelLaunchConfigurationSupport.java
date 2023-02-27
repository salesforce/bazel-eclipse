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
package com.salesforce.bazel.eclipse.launch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfos;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelTargetKind;

/**
 * Supporting logic for Bazel Launch Configurations.
 */
class BazelLaunchConfigurationSupport {

    /**
     * Attributes stored in the Bazel Launch Configuration.
     */
    enum BazelLaunchConfigAttributes {

        /*
         * The name of the Eclipse Project the Launch Configuration is for, such that the IProject instance can be retrieved using
         * ResourcesPlugin.getWorkspace().getRoot().getProject($projectName$).
         */
        PROJECT("project"),

        /*
         * The Bazel Label that is used to run the Launch Configuration using "bazel test" or "bazel run".
         */
        LABEL("label"),

        /*
         * The type of target the label is pointing to, for example "java_binary" or "java_test".
         * @see {link com.salesforce.bazel.eclipse.model.TargetKind}
         */
        TARGET_KIND("target_kind"),

        /*
         * List of arguments added to the cmdline when running the launch configuration.
         *
         * These arguments are not user specified.
         */
        INTERNAL_BAZEL_ARGS("internal_bazel_args"),

        /*
         * List of arguments added to the cmdline when running the launch configuration.
         *
         * These arguments are user specified.
         */
        USER_BAZEL_ARGS("user_bazel_args");

        private final String attributeName;

        BazelLaunchConfigAttributes(String attributeName) {
            this.attributeName = attributeName;
        }

        String getAttributeName() {
            return "com.salesforce.bazel.eclipse.launch." + attributeName;
        }

        List<String> getListValue(ILaunchConfiguration configuration) {
            try {
                return configuration.getAttribute(getAttributeName(), Collections.emptyList());
            } catch (CoreException ex) {
                LOG.error("Failed to load attribute value {}", ex, getAttributeName());
                return Collections.emptyList();
            }
        }

        String getStringValue(ILaunchConfiguration configuration) {
            try {
                return configuration.getAttribute(getAttributeName(), (String) null);
            } catch (CoreException ex) {
                LOG.error("Failed to load attribute value {}", ex, getAttributeName());
                return null;
            }
        }
    }

    /**
     * Groups a BazelLabel with its TargetKind.
     */
    static class TypedBazelLabel {

        private final BazelLabel bazelLabel;
        private final BazelTargetKind targetKind;

        TypedBazelLabel(BazelLabel bazelLabel, BazelTargetKind targetKind) {
            this.bazelLabel = Objects.requireNonNull(bazelLabel);
            this.targetKind = Objects.requireNonNull(targetKind);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof TypedBazelLabel o) {
                return bazelLabel.equals(o.bazelLabel) && (targetKind == o.targetKind);
            }
            return false;
        }

        BazelLabel getBazelLabel() {
            return bazelLabel;
        }

        BazelTargetKind getTargetKind() {
            return targetKind;
        }

        @Override
        public int hashCode() {
            return bazelLabel.hashCode() ^ targetKind.hashCode();
        }

        @Override
        public String toString() {
            return bazelLabel.toString() + " [" + targetKind.getKindName() + "] ";
        }
    }

    private static Logger LOG = LoggerFactory.getLogger(BazelLaunchConfigurationSupport.class);

    private static Set<BazelTargetKind> LAUNCHABLE_TARGET_KINDS = null;

    static {
        List<BazelTargetKind> targets = new ArrayList<>();
        for (BazelTargetKind kind : BazelTargetKind.getRegisteredKinds().values()) {
            // the expectation is that we'll only get java_binary targets
            // there's nothing wrong with getting other target kinds here,
            // but the target selection ui isn't that great, it should have
            // a better way to distinguish between different target kinds
            if (kind.isRunnable()) {
                targets.add(kind);
            }
        }
        LAUNCHABLE_TARGET_KINDS = Set.copyOf(targets);
    }

    private static AspectTargetInfos computeAspectTargetInfos(IProject project,
            BazelWorkspaceCommandRunner bazelRunner) {
        var bazelProjectManager = ComponentContext.getInstance().getProjectManager();
        try {
            // TODO switch this to use the BazelBuildFile value object
            var projectName = project.getName();
            var bazelProject = bazelProjectManager.getProject(projectName);
            var targets = bazelProjectManager.getConfiguredBazelTargets(bazelProject, false);
            var targetInfos = bazelRunner.getAspectTargetInfos(targets.getConfiguredTargets(),
                "launcher:computeAspectTargetInfos");
            return AspectTargetInfos.fromSets(targetInfos.values());
        } catch (IOException | InterruptedException | BazelCommandLineToolConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Returns all AspectTargetInfo instances that represent targets of the specified type, for the specified project.
     */
    Collection<AspectTargetInfo> getAspectTargetInfosForProject(IProject project, Set<BazelTargetKind> targetTypes) {
        var bazelRunner = EclipseBazelWorkspaceContext.getInstance().getWorkspaceCommandRunner();
        var apis = computeAspectTargetInfos(project, bazelRunner);
        return apis.lookupByTargetKind(targetTypes);
    }

    /**
     * Returns all Bazel targets of the specified type, for the specified project.
     */
    List<TypedBazelLabel> getBazelTargetsForProject(IProject project, Set<BazelTargetKind> targetTypes) {
        List<TypedBazelLabel> typedBazelLabels = new ArrayList<>();
        for (AspectTargetInfo api : getAspectTargetInfosForProject(project, targetTypes)) {
            var label = api.getLabel();
            var kind = api.getKind();
            typedBazelLabels.add(new TypedBazelLabel(label, kind));
        }
        return typedBazelLabels;
    }

    /**
     * Returns all runnable AspectTargetInfo instances for the specified project.
     *
     * @see {@link BazelTargetKind#isRunnable()}
     */
    Collection<AspectTargetInfo> getLaunchableAspectTargetInfosForProject(IProject project) {
        return getAspectTargetInfosForProject(project, LAUNCHABLE_TARGET_KINDS);
    }

    /**
     * Returns all runnable Bazel targets for the specified project.
     *
     * @see {@link BazelTargetKind#isRunnable()}
     */
    List<TypedBazelLabel> getLaunchableBazelTargetsForProject(IProject project) {
        return getBazelTargetsForProject(project, LAUNCHABLE_TARGET_KINDS);
    }

    void populateBazelLaunchConfig(ILaunchConfigurationWorkingCopy config, String projectName, BazelLabel label,
            BazelTargetKind targetKind) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(projectName);

        var labelStr = label == null ? null : label.getLabelPath();
        var kindStr = targetKind == null ? null : targetKind.getKindName();
        config.setAttribute(BazelLaunchConfigAttributes.PROJECT.getAttributeName(), projectName);
        config.setAttribute(BazelLaunchConfigAttributes.LABEL.getAttributeName(), labelStr);
        config.setAttribute(BazelLaunchConfigAttributes.TARGET_KIND.getAttributeName(), kindStr);
    }

    void setLaunchConfigDefaults(ILaunchConfigurationWorkingCopy config) {
        // this is required so that the red button in the console view is enabled and able to
        // terminate the running jvm while it is being debugged
        config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_ALLOW_TERMINATE, true);
    }

}
