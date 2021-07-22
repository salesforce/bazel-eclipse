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
import java.util.Collection;
import java.util.List;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.util.BazelConstants;

/**
 * Discovers well know paths in a bazel project. Invoked during import.
 */
class EclipseProjectStructureInspector {
    private static final LogHelper LOG = LogHelper.log(EclipseProjectStructureInspector.class);

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

        // TODO TODO TODO performance during import
        // During import we are getting in here multiple times for the same package node; this is expensive
        // and so we should have a short lived cache to respond quickly for duplicate queries

        // add this node buildable target
        File workspaceRootDir = packageNode.getWorkspaceRootDirectory();
        String bazelWorkspaceRootPath = FSPathHelper.getCanonicalPathStringSafely(workspaceRootDir);
        String bazelPackageFSRelativePath = packageNode.getBazelPackageFSRelativePath();
        File packageDir = new File(workspaceRootDir, bazelPackageFSRelativePath);

        // first check for the common case of Maven-like directory structure; this is the quickest way
        // to find the source files for a project

        // MAVEN MAIN SRC
        String mainSrcRelPath = bazelPackageFSRelativePath + File.separator + "src" + File.separator
                + "main" + File.separator + "java";
        File mainSrcDir = new File(bazelWorkspaceRootPath + File.separator + mainSrcRelPath);
        if (mainSrcDir.exists()) {
            packageSourceCodeFSPaths.add(mainSrcRelPath);
            foundSourceCodePaths = true;
        }

        // MAVEN MAIN RESOURCES
        String mainResourcesRelPath = bazelPackageFSRelativePath + File.separator + "src"
                + File.separator + "main" + File.separator + "resources";
        File mainResourcesDir = new File(bazelWorkspaceRootPath + File.separator + mainResourcesRelPath);
        if (mainResourcesDir.exists()) {
            packageSourceCodeFSPaths.add(mainResourcesRelPath);
            foundSourceCodePaths = true;
        }

        // MAVEN TEST SRC
        String testSrcRelPath = bazelPackageFSRelativePath + File.separator + "src" + File.separator
                + "test" + File.separator + "java";
        File testSrcDir = new File(bazelWorkspaceRootPath + File.separator + testSrcRelPath);
        if (testSrcDir.exists()) {
            packageSourceCodeFSPaths.add(testSrcRelPath);
            foundSourceCodePaths = true;
        }
        // MAVEN TEST RESOURCES
        String testResourcesRelPath = bazelPackageFSRelativePath + File.separator + "src"
                + File.separator + "test" + File.separator + "resources";
        File testResourcesDir = new File(bazelWorkspaceRootPath + File.separator + testResourcesRelPath);
        if (testResourcesDir.exists()) {
            packageSourceCodeFSPaths.add(testResourcesRelPath);
            foundSourceCodePaths = true;
        }

        if (!foundSourceCodePaths) {
            // the package is not following Maven conventions  NON_CONFORMING PROJECT SUPPORT
            // https://git.soma.salesforce.com/services/bazel-eclipse/blob/master/docs/conforming_java_packages.md
            // tracked as ISSUE #8  https://github.com/salesforce/bazel-eclipse/issues/8

            // we didn't find any Maven style source path, so we need to do the expensive Bazel Query option
            BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
            BazelWorkspaceCommandRunner commandRunner =
                    BazelPluginActivator.getBazelCommandManager().getWorkspaceCommandRunner(bazelWorkspace);
            BazelLabel packageLabel =
                    new BazelLabel(bazelPackageFSRelativePath, BazelLabel.BAZEL_WILDCARD_ALLTARGETS_STAR);
            Collection<String> results = null;
            try {
                results = commandRunner.querySourceFilesForTarget(workspaceRootDir, packageLabel);
            } catch (Exception anyE) {
                LOG.error("Failed querying package [{}] for source files.", anyE, packageLabel);
            }

            if ((results != null) && (results.size() > 0)) {
                LOG.warn(
                    "Non-Maven layouts are not yet supported by BEF. Package {} Issue: https://github.com/salesforce/bazel-eclipse/issues/8",
                    packageLabel);
                // the results will contain paths like this:
                //   source/dev/com/salesforce/foo/Bar.java
                // but it can also have non-java source files, so we need to check for that

                // Remaining work:
                // 1. we need to introspect the source file for the the package path (.java we need to determine com/salesforce/foo)
                // 2. test JUnit launchers classpath are broken

                // PJL fake example
                //packageSourceCodeFSPaths.add(bazelPackageFSRelativePath + File.separator + "source" + File.separator + "dev");
                // packageSourceCodeFSPaths.add(bazelPackageFSRelativePath + File.separator + "source" + File.separator + "test");
                //foundSourceCodePaths = true;
            } else {
                LOG.info("Did not find any source files for package [{}], ignoring for import.", packageLabel);
            }
        }

        // proto files are generally in the toplevel folder (not a Maven convention, but common), lets check for those now
        if (packageDir.list(new ProtoFileFilter()).length > 0) {
            packageSourceCodeFSPaths.add(packageNode.getBazelPackageFSRelativePath());
        }

        if (foundSourceCodePaths) {
            String packagePath = packageNode.getBazelPackageFSRelativePath();
            String labelPath = packagePath.replace(FSPathHelper.WINDOWS_BACKSLASH, BazelLabel.BAZEL_SLASH); // convert Windows style paths to Bazel label paths
            for (String target : BazelConstants.DEFAULT_PACKAGE_TARGETS) {
                bazelTargets.add(new BazelLabel(labelPath, target));
            }
        }
    }

    private static class ProtoFileFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            if (name.endsWith(".proto")) {
                return true;
            }
            return false;
        }
    }
}
