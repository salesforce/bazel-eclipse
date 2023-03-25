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

import java.util.List;

import com.salesforce.bazel.sdk.model.BazelPackageInfoOld;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;

/**
 * A BazelProjectOld is a logical concept that has no concrete artifact in the Bazel workspace. It is a BazelPackage
 * that represents a logical module or major component of a system. In the Maven world, this would be a Maven module.
 * <p>
 * A BazelProjectOld can contain one or more Bazel packages.
 *
 * @deprecated because Bazel has no concept of projects this does not belong into the SDK
 */
@Deprecated
public class BazelProjectOld {

    // FIXME: BazelProjectOld is terrible; it should have proper API instead of this none-sense abstraction
    // need IProject and IJavaProject support

    public String name;

    public List<BazelPackageInfoOld> bazelPackages;
    public BazelProjectManager bazelProjectManager;
    public ProjectStructure projectStructure = new ProjectStructure();

    /**
     * the tool environment (e.g. IDE) may provide a project implementation object of its own, that is stored here
     */
    public Object projectImpl;

    public BazelProjectOld(String name) {
        this.name = name;
    }

    public BazelProjectOld(String name, List<BazelPackageInfoOld> packages) {
        this.name = name;
        bazelPackages = packages;
    }

    public BazelProjectOld(String name, List<BazelPackageInfoOld> packages, Object projectImpl) {
        this.name = name;
        bazelPackages = packages;
        this.projectImpl = projectImpl;
    }

    public BazelProjectOld(String name, Object projectImpl) {
        this.name = name;
        this.projectImpl = projectImpl;
    }

    public BazelProjectOld(String name, Object projectImpl, ProjectStructure projectStructure) {
        this.name = name;
        this.projectImpl = projectImpl;
        this.projectStructure = projectStructure;

    }

    public Object getProjectImpl() {
        return projectImpl;
    }

    public List<BazelPackageInfoOld> getProjectPackages() {
        return bazelPackages;
    }

    public ProjectStructure getProjectStructure() {
        return projectStructure;
    }

    /**
     * Merges this project with the passed project. For each element, this project will win out if it has meaningful
     * data, else the olderProject data will be used for that element.
     */
    public void merge(BazelProjectOld olderProject) {
        if (olderProject == null) {
            return;
        }

        if (!olderProject.name.equals(name)) {
            throw new IllegalArgumentException(
                    "failed trying to merge BazelProjects of different names: " + name + " and " + olderProject.name);
        }

        if ((bazelPackages != null) && (bazelPackages.size() > 0)) {
            olderProject.bazelPackages = bazelPackages;
        } else {
            bazelPackages = olderProject.bazelPackages;
        }

        if (projectStructure != null) {
            projectStructure.merge(olderProject.projectStructure);
        } else {
            projectStructure = olderProject.projectStructure;
        }

    }
}
