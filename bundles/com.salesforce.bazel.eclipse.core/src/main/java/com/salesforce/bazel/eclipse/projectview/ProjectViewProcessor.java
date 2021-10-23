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
package com.salesforce.bazel.eclipse.projectview;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.project.ProjectViewPackageLocation;
import com.salesforce.bazel.sdk.util.BazelDirectoryStructureUtil;

final class ProjectViewProcessor {

    private static final LogHelper LOG = LogHelper.log(ProjectViewProcessor.class);

    /**
     * Expands parent directories in the specified ProjectView to concrete Bazel Packages.
     *
     * Populates the given invalidDirectories list with...invalid directories.
     *
     * @return Expanded ProjectView, if there are no errors.
     */
    static ProjectView resolvePackages(ProjectView projectView, List<BazelPackageLocation> invalidDirectories) {
        File rootDir = projectView.getWorkspaceRootDirectory();
        List<BazelPackageLocation> directories = new ArrayList<>();
        List<BazelPackageLocation> additionalDirectories = new ArrayList<>();
        for (BazelPackageLocation packageLocation : projectView.getDirectories()) {

            String directory = packageLocation.getBazelPackageFSRelativePath();
            if (BazelDirectoryStructureUtil.isBazelPackage(rootDir, directory)) {
                // no change, just add the same package
                directories.add(packageLocation);
            } else {
                // look for BUILD files below this location
                List<String> additionalPackages = BazelDirectoryStructureUtil.findBazelPackages(rootDir, directory);
                LOG.info("Found " + additionalPackages.size() + " packages under " + directory);
                if (additionalPackages.isEmpty()) {
                    invalidDirectories.add(packageLocation);
                } else {
                    additionalDirectories.addAll(additionalPackages.stream()
                            .map(p -> new ProjectViewPackageLocation(rootDir, p)).collect(Collectors.toList()));
                }
            }
        }
        directories.addAll(additionalDirectories);

        return invalidDirectories.isEmpty() ? new ProjectView(rootDir, directories, projectView.getTargets())
                : projectView;
    }

}
