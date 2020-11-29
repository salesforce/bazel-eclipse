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
package com.salesforce.bazel.sdk.project;

import java.util.Set;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.model.BazelBuildFile;

/**
 * Object that encapsulates the logic and state regarding the active targets configured for a BazelProject.
 */
public class BazelProjectTargets {
    private BazelProject project;
    private String projectBazelLabel;

    /**
     * This is the list of targets listed as listed in the project preferences. This may contain a single entry that is
     * the wildcard target (:*), or it can be a list of specific targets
     */
    private Set<String> configuredTargets = new TreeSet<>();

    /**
     * Convenience flag that indicates that the activatedTargets list contains one entry and it is the wildcard entry
     */
    private boolean isActivatedWildcardTarget = false;

    /**
     * Contains the list of targets configured for building/testing. This will be the same as configuredTargets if
     * isActivatedWildcardTarget==false, or will be the actual list of all targets found in the BUILD file if
     * isActivatedWildcardTarget==true
     */
    private Set<String> actualTargets;

    public BazelProjectTargets(BazelProject project, String projectBazelLabel) {
        this.project = project;
        this.projectBazelLabel = projectBazelLabel;
    }

    // TODO weave these into the constructor

    public void activateWildcardTarget(String wildcardTarget) {
        this.isActivatedWildcardTarget = true;
        this.configuredTargets.add(projectBazelLabel + ":" + wildcardTarget);
    }

    public void activateSpecificTargets(Set<String> activatedTargets) {
        this.isActivatedWildcardTarget = false;
        this.configuredTargets = activatedTargets;
        this.actualTargets = activatedTargets;
    }

    // CONSUMER API

    public BazelProject getProject() {
        return this.project;
    }

    public Set<String> getConfiguredTargets() {
        return this.configuredTargets;
    }

    public Set<String> getActualTargets(BazelBuildFile bazelBuildFile) {
        if (this.isActivatedWildcardTarget) {
            this.actualTargets = bazelBuildFile.getAllTargetLabels();
        }

        return this.actualTargets;
    }

    public boolean isActivatedWildcardTarget() {
        return this.isActivatedWildcardTarget;
    }

    public boolean isAllTargetsDeactivated() {
        return this.configuredTargets.size() == 0;
    }

    @Override
    public String toString() {
        return String.valueOf(getConfiguredTargets());
    }

}