/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
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
 */
package com.salesforce.bazel.sdk.lang.jvm;

import java.io.File;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;
import com.salesforce.bazel.sdk.project.structure.ProjectStructureStrategy;

/**
 * Project structure strategy that is optimized to quickly determine if this project is laid out with Maven conventions
 * (src/main/java, src/test/java). It is much faster than the fallback Bazel Query strategy.
 * <p>
 * But there is a risk in using this strategy. If a package has src/main/java and src/test/java, and yet has additional
 * source folders, those will be missed. The tool env should allow the user to disable this strategy for certain
 * workspaces because of this.
 */
public class MavenProjectStructureStrategy extends ProjectStructureStrategy {
    private static final LogHelper LOG = LogHelper.log(MavenProjectStructureStrategy.class);

    @Override
    public ProjectStructure doStructureAnalysis(BazelWorkspace bazelWorkspace, BazelPackageLocation packageNode,
            BazelWorkspaceCommandRunner commandRunner) {
        ProjectStructure result = new ProjectStructure();

        // NOTE: order of adding the result.packageSourceCodeFSPaths array is important. Eclipse
        // will honor that order in the project explorer

        File workspaceRootDir = bazelWorkspace.getBazelWorkspaceRootDirectory();
        String packageRelPath = packageNode.getBazelPackageFSRelativePath();

        // MAVEN MAIN SRC
        String mainSrcRelPath =
                packageRelPath + File.separator + "src" + File.separator + "main" + File.separator + "java";
        File mainSrcDir = new File(workspaceRootDir, mainSrcRelPath);
        if (mainSrcDir.exists()) {
            result.packageSourceCodeFSPaths.add(mainSrcRelPath);
        } else {
            // by design, this strategy will only be ineffect if src/main/java exists
            LOG.info("Package {} does not have src/main/java so is not a Maven-like project", packageRelPath);
            return null;
        }

        // MAVEN MAIN RESOURCES
        String mainResourcesRelPath =
                packageRelPath + File.separator + "src" + File.separator + "main" + File.separator + "resources";
        File mainResourcesDir = new File(workspaceRootDir, mainResourcesRelPath);
        if (mainResourcesDir.exists()) {
            result.packageSourceCodeFSPaths.add(mainResourcesRelPath);
        }

        // MAVEN TEST SRC
        String testSrcRelPath =
                packageRelPath + File.separator + "src" + File.separator + "test" + File.separator + "java";
        File testSrcDir = new File(workspaceRootDir, testSrcRelPath);
        if (testSrcDir.exists()) {
            result.packageSourceCodeFSPaths.add(testSrcRelPath);
        } else {
            // by design, this strategy will only be ineffect if src/test/java exists
            LOG.info("Package {} does not have src/test/java so is not a Maven-like project", packageRelPath);
            return null;
        }

        // MAVEN TEST RESOURCES
        String testResourcesRelPath =
                packageRelPath + File.separator + "src" + File.separator + "test" + File.separator + "resources";
        File testResourcesDir = new File(workspaceRootDir, testResourcesRelPath);
        if (testResourcesDir.exists()) {
            result.packageSourceCodeFSPaths.add(testResourcesRelPath);
        }

        return result;
    }

}
