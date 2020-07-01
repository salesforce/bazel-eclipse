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
package com.salesforce.bazel.eclipse.importer;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import com.salesforce.bazel.eclipse.config.BazelProjectConfigurator;
import com.salesforce.bazel.sdk.model.BazelPackageInfo;

/**
 * Scans a Bazel workspace looking for Java packages (BUILD files that have java_binary or java_library targets). It is
 * assumed that the user will provide the root workspace directory (where the WORKSPACE file is) and we will scan the
 * subtree below that.
 * <p>
 * TODO the current Import UI approach is not scalable beyond a few hundred Bazel Java packages.
 * <p>
 * It will be difficult for a user to select the projects they want if there are hundreds/thousands of boxes in the tree
 * view. Since we stole this Import UI design from m2e, we are stuck with their approach for now. In Maven it is
 * unlikely that you have a single parent module with hundreds of submodules (ha ha, I know of one case of that) so they
 * didn't optimize import for huge numbers of modules.
 * <p>
 * In the future, we should allow the user to provide the workspace root (but not load anything). Then there would be
 * two modes:
 * <p>
 * 1. the existing design where we scan the entire workspace and suggest to add all packages
 * <p>
 * 2. do not scan the workspace and populate the tree control, but wait for the user to interact with a Search Box
 * control. They can selectively search for what they want and add projects (e.g. 'basic-rest-se' and then click the
 * basic-rest-service) and then optionally also import upstream and/or downstream deps.
 *
 * Or said another way, approach number 1 is a simple approach that does not consider the Bazel dependency tree. The
 * second approach would make use of Bazel Query to intelligently suggest other projects to import based on a small
 * selection picked by the user.
 * 
 * @author plaird
 */
public class BazelProjectImportScanner {

    // TODO BazelProjectImportScanner should be moved into the plugin-command project, but will require some refactoring
    // of BazelProjectConfigurator and maybe other collaborators
    
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
    public BazelPackageInfo getProjects(String rootDirectory) throws IOException {
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
        return getProjects(workspaceRootDir);
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
    public BazelPackageInfo getProjects(File rootDirectoryFile) throws IOException {
        if (rootDirectoryFile == null || !rootDirectoryFile.exists() || !rootDirectoryFile.isDirectory()) {
            // this is the initialization state of the wizard
            return null;
        }
        String rootDirectory = rootDirectoryFile.getCanonicalPath();

        // TODO the correct way to do this is put the configurator on another thread, and allow it to update the progress monitor.
        // Do it on-thread for now as it is easiest.

        BazelProjectConfigurator configurator = new BazelProjectConfigurator();
        Set<File> projects = configurator.findConfigurableLocations(rootDirectoryFile, null);

        BazelPackageInfo workspace = new BazelPackageInfo(rootDirectoryFile);

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
