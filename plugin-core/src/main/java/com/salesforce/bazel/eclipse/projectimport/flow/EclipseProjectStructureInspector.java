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
 */
package com.salesforce.bazel.eclipse.projectimport.flow;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.util.BazelConstants;
import com.salesforce.bazel.sdk.util.BazelPathHelper;

/**
 * Discovers well know paths in a bazel project.
 */
class EclipseProjectStructureInspector {

    private final List<String> packageSourceCodeFSPaths = new ArrayList<>();
    private final List<BazelLabel> bazelTargets = new ArrayList<>();

    EclipseProjectStructureInspector(BazelPackageLocation packageNode) {
        computePackageSourceCodePaths(packageNode);
    }

    List<String> getPackageSourceCodeFSPaths() {
        return packageSourceCodeFSPaths;
    }

    List<BazelLabel> getBazelTargets() {
        return bazelTargets;
    }

    private void computePackageSourceCodePaths(BazelPackageLocation packageNode) {
        boolean foundSourceCodePaths = false;

        // add this node buildable target
        String bazelPackageRootDirectory =
                BazelPathHelper.getCanonicalPathStringSafely(packageNode.getWorkspaceRootDirectory());
        File packageDirectory =
                new File(packageNode.getWorkspaceRootDirectory(), packageNode.getBazelPackageFSRelativePath());

        // TODO here is where we assume that the Java project is conforming  NON_CONFORMING PROJECT SUPPORT
        // https://git.soma.salesforce.com/services/bazel-eclipse/blob/master/docs/conforming_java_packages.md
        // tracked as ISSUE #8  https://github.com/salesforce/bazel-eclipse/issues/8

        // MAIN SRC
        String mainSrcRelPath = packageNode.getBazelPackageFSRelativePath() + File.separator + "src" + File.separator
                + "main" + File.separator + "java";
        File mainSrcDir = new File(bazelPackageRootDirectory + File.separator + mainSrcRelPath);
        if (mainSrcDir.exists()) {
            this.packageSourceCodeFSPaths.add(mainSrcRelPath);
            foundSourceCodePaths = true;
        }
        // MAIN RESOURCES
        String mainResourcesRelPath = packageNode.getBazelPackageFSRelativePath() + File.separator + "src"
                + File.separator + "main" + File.separator + "resources";
        File mainResourcesDir = new File(bazelPackageRootDirectory + File.separator + mainResourcesRelPath);
        if (mainResourcesDir.exists()) {
            this.packageSourceCodeFSPaths.add(mainResourcesRelPath);
            foundSourceCodePaths = true;
        }

        // TEST SRC
        String testSrcRelPath = packageNode.getBazelPackageFSRelativePath() + File.separator + "src" + File.separator
                + "test" + File.separator + "java";
        File testSrcDir = new File(bazelPackageRootDirectory + File.separator + testSrcRelPath);
        if (testSrcDir.exists()) {
            this.packageSourceCodeFSPaths.add(testSrcRelPath);
            foundSourceCodePaths = true;
        }
        // TEST RESOURCES
        String testResourcesRelPath = packageNode.getBazelPackageFSRelativePath() + File.separator + "src"
                + File.separator + "test" + File.separator + "resources";
        File testResourcesDir = new File(bazelPackageRootDirectory + File.separator + testResourcesRelPath);
        if (testResourcesDir.exists()) {
            this.packageSourceCodeFSPaths.add(testResourcesRelPath);
            foundSourceCodePaths = true;
        }

        // proto files are generally in the toplevel folder, lets check for those now
        if (packageDirectory.list(new BEFSourceCodeFilter()).length > 0) {
            this.packageSourceCodeFSPaths.add(packageNode.getBazelPackageFSRelativePath());
        }

        if (foundSourceCodePaths) {
            String packagePath = packageNode.getBazelPackageFSRelativePath();
            for (String target : BazelConstants.DEFAULT_PACKAGE_TARGETS) {
                this.bazelTargets.add(new BazelLabel(packagePath, target));
            }
        }
    }

    private static class BEFSourceCodeFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            if (name.endsWith(".proto")) {
                return true;
            }
            return false;
        }
    }
}
