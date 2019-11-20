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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.BazelCommandFacade;
import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.eclipse.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.eclipse.model.AspectPackageInfo;
import com.salesforce.bazel.eclipse.model.AspectPackageInfos;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.TargetKind;

/**
 * Supporting logic for Bazel Launch Configurations.
 *
 * @author stoens
 * @since summer 2019
 *
 */
class BazelLaunchConfigurationSupport {

    /**
     * Groups a BazelLabel with its TargetKind.
     */
    static class TypedBazelLabel {

        private final BazelLabel bazelLabel;
        private final TargetKind targetKind;

        TypedBazelLabel(BazelLabel bazelLabel, TargetKind targetKind) {
            this.bazelLabel = Objects.requireNonNull(bazelLabel);
            this.targetKind = Objects.requireNonNull(targetKind);
        }

        BazelLabel getBazelLabel() {
            return bazelLabel;
        }

        TargetKind getTargetKind() {
            return targetKind;
        }

        @Override
        public int hashCode() {
            return bazelLabel.hashCode() ^ targetKind.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof TypedBazelLabel) {
                TypedBazelLabel o = (TypedBazelLabel) other;
                return bazelLabel.equals(o.bazelLabel) && targetKind == o.targetKind;
            }
            return false;
        }

        @Override
        public String toString() {
            return bazelLabel.toString() + " [" + targetKind.getKind() + "] ";
        }
    }

    private static EnumSet<TargetKind> RUNNABLE_TARGETS = EnumSet.copyOf(
        Arrays.asList(TargetKind.values()).stream().filter(TargetKind::isRunnable).collect(Collectors.toList()));

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
         * A Map<String, String> of arguments that are added to the bazel command line.
         * The keys of the Map are argument names and the values are argument values; each
         * Map Entry is added to the bazel command line as <key>=<value>.
         */
        INTERNAL_BAZEL_ARGS("internal_bazel_args");

        private final String attributeName;

        private BazelLaunchConfigAttributes(String attributeName) {
            this.attributeName = attributeName;
        }

        String getAttributeName() {
            return "com.salesforce.bazel.eclipse.launch." + attributeName;
        }
    }
    
    void populateBazelLaunchConfig(ILaunchConfigurationWorkingCopy config, String projectName, BazelLabel label, TargetKind targetKind) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(projectName);
        config.setAttribute(BazelLaunchConfigAttributes.PROJECT.getAttributeName(), projectName);
        config.setAttribute(BazelLaunchConfigAttributes.LABEL.getAttributeName(), label == null ? null : label.getLabel());
        config.setAttribute(BazelLaunchConfigAttributes.TARGET_KIND.getAttributeName(),
            targetKind == null ? null : targetKind.getKind());
    }


    BazelWorkspaceCommandRunner getBazelCommandRunnerForProject(IProject project) {
        BazelCommandFacade bazelCommandFacade = BazelPluginActivator.getBazelCommandFacade();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandFacade.getWorkspaceCommandRunner(BazelPluginActivator.getBazelWorkspaceRootDirectory());
        return bazelWorkspaceCmdRunner;
    }

    /**
     * Returns all runnable Bazel targets for the specified project.
     * 
     * @see {@link TargetKind#isRunnable()}
     */
    Collection<TypedBazelLabel> getRunnableBazelTargetsForProject(IProject project) {
        return getBazelTargetsForProject(project, RUNNABLE_TARGETS);
    }
    
    /**
     * Returns all runnable AspectPackageInfo instances for the specified project.
     * 
     * @see {@link TargetKind#isRunnable()}
     */
    Collection<AspectPackageInfo> getRunnableAspectPackageInfosForProject(IProject project) {
        return getAspectPackageInfosForProject(project, RUNNABLE_TARGETS);
    }
    
    /**
     * Returns all AspectPackageInfo instances that represent targets of the specified type, for the specified project.
     */
    Collection<AspectPackageInfo> getAspectPackageInfosForProject(IProject project, EnumSet<TargetKind> targetTypes) {
        BazelWorkspaceCommandRunner bazelRunner = getBazelCommandRunnerForProject(project);
        AspectPackageInfos apis = computeAspectPackageInfos(project, bazelRunner, WorkProgressMonitor.NOOP);
        return apis.lookupByTargetKind(targetTypes);
    }

    /**
     * Returns all Bazel targets of the specified type, for the specified project.
     */
    Collection<TypedBazelLabel> getBazelTargetsForProject(IProject project, EnumSet<TargetKind> targetTypes) {
        List<TypedBazelLabel> typedBazelLabels = new ArrayList<>();
        for (AspectPackageInfo api : getAspectPackageInfosForProject(project, targetTypes)) {
            BazelLabel label = new BazelLabel(api.getLabel());
            TargetKind kind = TargetKind.valueOfIgnoresCase(api.getKind());
            typedBazelLabels.add(new TypedBazelLabel(label, kind));
        }
        return typedBazelLabels;
    }
    
    private static AspectPackageInfos computeAspectPackageInfos(IProject project, BazelWorkspaceCommandRunner bazelRunner,
            WorkProgressMonitor monitor) {
        try {
            List<String> targets = BazelEclipseProjectSupport.getBazelTargetsForEclipseProject(project, false);
            Map<String, AspectPackageInfo> packageInfos = bazelRunner.getAspectPackageInfos(project.getName(), targets,
                monitor, "launcher:computeAspectPackageInfos");
            return new AspectPackageInfos(packageInfos.values());
        } catch (IOException | InterruptedException | BazelCommandLineToolConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
