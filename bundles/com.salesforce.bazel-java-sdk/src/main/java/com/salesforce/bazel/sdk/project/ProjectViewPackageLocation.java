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
import java.util.List;
import java.util.Objects;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Represents a line in a project view file.
 * <p>
 * TODO having this distinct from BazelPackageInfo adds complexity to the import logic. Revisit whether we can merge it.
 *
 */
public class ProjectViewPackageLocation implements BazelPackageLocation {

    private final File workspaceRootDirectory;
    private final String packagePath;
    private final List<BazelLabel> targets;

    public ProjectViewPackageLocation(File workspaceRootDirectory, String packagePath) {
        this(workspaceRootDirectory, packagePath, null);
    }

    ProjectViewPackageLocation(File workspaceRootDirectory, String packagePath, List<BazelLabel> targets) {
        this.workspaceRootDirectory = Objects.requireNonNull(workspaceRootDirectory);
        this.packagePath = Objects.requireNonNull(packagePath);
        if (new File(this.packagePath).isAbsolute()) {
            throw new IllegalArgumentException("[" + packagePath + "] must be relative");
        }
        this.targets = targets;
    }

    @Override
    public String getBazelPackageNameLastSegment() {
        return new File(packagePath).getName();
    }

    @Override
    public String getBazelPackageFSRelativePath() {
        return packagePath;
    }

    @Override
    public File getWorkspaceRootDirectory() {
        return workspaceRootDirectory;
    }

    @Override
    public boolean isWorkspaceRoot() {
        return packagePath.isEmpty();
    }

    @Override
    public String getBazelPackageName() {
        if ("".equals(packagePath)) {
            // the caller is referring to the WORKSPACE root
            return BazelLabel.BAZEL_ROOT_SLASHES;
        }
        return BazelLabel.BAZEL_ROOT_SLASHES + packagePath;
    }

    @Override
    public int hashCode() {
        return workspaceRootDirectory.hashCode() ^ packagePath.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof ProjectViewPackageLocation) {
            ProjectViewPackageLocation o = (ProjectViewPackageLocation) other;
            return workspaceRootDirectory.equals(o.workspaceRootDirectory) && packagePath.equals(o.packagePath);
        }
        return false;
    }

    @Override
    public String toString() {
        return "package path: " + packagePath;
    }

    @Override
    public List<BazelPackageLocation> gatherChildren() {
        // TODO hard to implement, this class is planned for a rework
        return null;
    }

    @Override
    public List<BazelPackageLocation> gatherChildren(String pathFilter) {
        // TODO hard to implement, this class is planned for a rework
        return null;
    }

    @Override
    public List<BazelLabel> getBazelTargets() {
        return targets;
    }

}
