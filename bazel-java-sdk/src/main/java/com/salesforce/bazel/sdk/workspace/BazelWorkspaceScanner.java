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
package com.salesforce.bazel.sdk.workspace;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.model.BazelPackageInfo;

/**
 * Scans a Bazel workspace looking for Java packages (BUILD files that have java_binary or java_library targets). It is
 * assumed that the user will provide the root workspace directory (where the WORKSPACE file is) and we will scan the
 * subtree below that.
 */
public class BazelWorkspaceScanner {

    public static String getBazelWorkspaceName(String bazelWorkspaceRootDirectory) {
        // TODO pull the workspace name out of the WORKSPACE file, until then use the directory name (e.g. bazel-demo)
        String bazelWorkspaceName = "workspace";
        if (bazelWorkspaceRootDirectory != null) {
            int lastSlash = bazelWorkspaceRootDirectory.lastIndexOf(File.separator);
            if (lastSlash >= 0 && (bazelWorkspaceRootDirectory.length() - lastSlash) > 3) {
                // add the directory name to the label, if it is meaningful (>3 chars)
                bazelWorkspaceName = bazelWorkspaceRootDirectory.substring(lastSlash + 1);
            } else {
                bazelWorkspaceName = bazelWorkspaceRootDirectory;
            }
        }
        return bazelWorkspaceName;
    }

    /**
     * Get a list of candidate Bazel packages to import. This list is provided to the user in the form of a tree
     * control.
     * <p>
     * Currently, the list returned will always be of size 1. It represents the root node of the scanned Bazel
     * workspace. The root node has child node references, and the tree expands from there.
     * <p>
     * TODO support scanning at an arbitrary location inside of a Bazel workspace (e.g. //projects/libs) and have the
     * scanner crawl up to the WORKSPACE root from there.
     *
     * @param rootDirectory
     *            the directory to scan, which must be the root node of a Bazel workspace
     * @return the workspace root BazelPackageInfo
     */
    public BazelPackageInfo getPackages(String rootDirectory) throws IOException {
        if (rootDirectory == null || rootDirectory.isEmpty()) {
            // this is the initialization state of the wizard
            return null;
        }
        File workspaceRootDir = new File(rootDirectory);
        try {
            workspaceRootDir = workspaceRootDir.getCanonicalFile();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return null;
        }
        return getPackages(workspaceRootDir);
    }

    /**
     * Get a list of candidate Bazel packages to import. This list is provided to the user in the form of a tree
     * control.
     * <p>
     * Currently, the list returned will always be of size 1. It represents the root node of the scanned Bazel
     * workspace. The root node has child node references, and the tree expands from there.
     * <p>
     * TODO support scanning at an arbitrary location inside of a Bazel workspace (e.g. //projects/libs) and have the
     * scanner crawl up to the WORKSPACE root from there.
     *
     * @param rootDirectory
     *            the directory to scan, which must be the root node of a Bazel workspace
     * @return the workspace root BazelPackageInfo
     */
    public BazelPackageInfo getPackages(File rootDirectoryFile) throws IOException {
        if (rootDirectoryFile == null || !rootDirectoryFile.exists() || !rootDirectoryFile.isDirectory()) {
            // this is the initialization state of the wizard
            return null;
        }
        String rootDirectory = rootDirectoryFile.getCanonicalPath();
        BazelPackageInfo workspace = new BazelPackageInfo(rootDirectoryFile);

        // TODO the correct way to do this is put the scan on another thread, and allow it to update the progress monitor.
        // Do it on-thread for now as it is easiest.

        Set<File> projects = new TreeSet<>();
        BazelPackageFinder packageFinder = new BazelPackageFinder();
        packageFinder.findBuildFileLocations(rootDirectoryFile, null, projects, 0);

        int sizeOfWorkspacePath = rootDirectory.length();
        for (File project : projects) {
            String projectPath = project.getCanonicalPath();

            if (projectPath.equals(rootDirectory)) {
                // root path, already created the root node
                continue;
            }

            // TODO ooh, this bazel package path manipulation seems error prone
            String relativePath = projectPath.substring(sizeOfWorkspacePath + 1);

            // instantiate the project info object, which will automatically hook itself to the appropriate parents
            new BazelPackageInfo(workspace, relativePath);
        }

        return workspace;
    }

}
