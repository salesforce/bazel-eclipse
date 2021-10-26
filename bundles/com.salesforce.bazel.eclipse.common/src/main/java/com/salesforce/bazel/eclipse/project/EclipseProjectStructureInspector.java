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
package com.salesforce.bazel.eclipse.project;

import java.io.File;
import java.io.FilenameFilter;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;
import com.salesforce.bazel.sdk.project.structure.ProjectStructureStrategy;
import com.salesforce.bazel.sdk.util.BazelConstants;

/**
 * Discovers well know paths in a bazel project. Invoked during import.
 * <p>
 * This can be expensive to compute, so you should be calling the getProjectStructure() method on ImportContext instead,
 * as it caches the results for the duration of the import.
 */
public class EclipseProjectStructureInspector {

    // TODO after cleaning this up, there might not be anything left of this operation in the Core plugin; it should
    // be all done in the SDK

    public static ProjectStructure computePackageSourceCodePaths(BazelPackageLocation packageNode, BazelWorkspace bazelWorkspace, BazelCommandManager bazelCommandManager) {
        ProjectStructure result;

        BazelWorkspaceCommandRunner commandRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        result = ProjectStructureStrategy.determineProjectStructure(bazelWorkspace, packageNode, commandRunner);

        if (result != null) {
            // we found some source paths

            File workspaceRootDir = packageNode.getWorkspaceRootDirectory();
            String bazelPackageFSRelativePath = packageNode.getBazelPackageFSRelativePath();
            File packageDir = new File(workspaceRootDir, bazelPackageFSRelativePath);

            // we dont do much for proto files (at least, not currently, see #60) so only
            // check for proto files if we already have other source files
            //   https://github.com/salesforce/bazel-eclipse/issues/60
            // proto files are generally in the toplevel folder (not a Maven convention, but common), lets check for those now
            // eventually we should use bazel query for these as well
            // TODO I don't think we need this anymore, we now surface all files in the root of package automatically
            if (packageDir.list(new ProtoFileFilter()).length > 0) {
                result.mainResourceDirFSPaths.add(packageNode.getBazelPackageFSRelativePath());
            }

            // TODO derive the list of active targets, this isnt right, we should be honoring the list we already have,
            // also these targets do not belong in the structure object
            String packagePath = packageNode.getBazelPackageFSRelativePath();
            String labelPath = packagePath.replace(FSPathHelper.WINDOWS_BACKSLASH, BazelLabel.BAZEL_SLASH); // convert Windows style paths to Bazel label paths
            for (String target : BazelConstants.DEFAULT_PACKAGE_TARGETS) {
                result.bazelTargets.add(new BazelLabel(labelPath, target));
            }
        }

        return result;
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
