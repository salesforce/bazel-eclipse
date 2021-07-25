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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmSourceFolderResolver;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.path.FSTree;
import com.salesforce.bazel.sdk.project.SourcePath;
import com.salesforce.bazel.sdk.util.BazelConstants;

/**
 * Discovers well know paths in a bazel project. Invoked during import.
 * <p>
 * This is expensive to compute, so you should be calling the getProjectStructure() method on ImportContext instead, as
 * it caches the results for the duration of the import.
 */
public class EclipseProjectStructureInspector {
    private static final LogHelper LOG = LogHelper.log(EclipseProjectStructureInspector.class);

    // We will always want Maven optimization enabled for official builds, but for internal testing we sometimes want
    // to disable this so we can be sure the bazel query path is working for some of our internal repos that happen to
    // follow Maven conventions. We should really make a pref for this TODO
    public static boolean ENABLE_MAVEN_OPTIMIZATION = true;

    public static EclipseProjectStructure computePackageSourceCodePaths(BazelPackageLocation packageNode) {
        EclipseProjectStructure result = null;

        // add this node buildable target
        File workspaceRootDir = packageNode.getWorkspaceRootDirectory();
        String bazelWorkspaceRootPath = FSPathHelper.getCanonicalPathStringSafely(workspaceRootDir);
        String bazelPackageFSRelativePath = packageNode.getBazelPackageFSRelativePath();
        File packageDir = new File(workspaceRootDir, bazelPackageFSRelativePath);

        // first check for the common case of Maven-like directory structure; this is the quickest way
        // to find the source files for a project
        if (ENABLE_MAVEN_OPTIMIZATION) {
            result = doCheapMavenStructureCheck(packageNode, workspaceRootDir, bazelWorkspaceRootPath,
                bazelPackageFSRelativePath, packageDir);
        }

        if (result == null) {
            // we didn't find any Maven style source paths, so we need to do the expensive Bazel Query option
            LOG.info("Starting Bazel Query strategy for finding source files in package {}...",
                bazelPackageFSRelativePath);
            result = doExpensiveBazelQueryStructureCheck(packageNode, workspaceRootDir, bazelWorkspaceRootPath,
                bazelPackageFSRelativePath, packageDir);
        }

        if (result != null) {
            // we found some source paths

            // we dont do much for proto files (at least, not currently, see #60) so only
            // check for proto files if we already have other source files
            //   https://github.com/salesforce/bazel-eclipse/issues/60
            // proto files are generally in the toplevel folder (not a Maven convention, but common), lets check for those now
            // eventually we should use bazel query for these as well
            if (packageDir.list(new ProtoFileFilter()).length > 0) {
                result.packageSourceCodeFSPaths.add(packageNode.getBazelPackageFSRelativePath());
            }

            // TODO derive the list of active targets, this isnt quite right, we should be honoring the list we already have
            String packagePath = packageNode.getBazelPackageFSRelativePath();
            String labelPath = packagePath.replace(FSPathHelper.WINDOWS_BACKSLASH, BazelLabel.BAZEL_SLASH); // convert Windows style paths to Bazel label paths
            for (String target : BazelConstants.DEFAULT_PACKAGE_TARGETS) {
                result.bazelTargets.add(new BazelLabel(labelPath, target));
            }
        }

        return result;
    }

    private static EclipseProjectStructure doCheapMavenStructureCheck(BazelPackageLocation packageNode,
            File workspaceRootDir, String bazelWorkspaceRootPath, String bazelPackageFSRelativePath, File packageDir) {
        EclipseProjectStructure result = new EclipseProjectStructure();

        // NOTE: order of adding the result.packageSourceCodeFSPaths array is important. Eclipse
        // will honor that order in the project explorer

        // MAVEN MAIN SRC
        String mainSrcRelPath = bazelPackageFSRelativePath + File.separator + "src" + File.separator
                + "main" + File.separator + "java";
        File mainSrcDir = new File(bazelWorkspaceRootPath + File.separator + mainSrcRelPath);
        if (mainSrcDir.exists()) {
            result.packageSourceCodeFSPaths.add(mainSrcRelPath);
        } else {
            // by design, this strategy will only be ineffect if src/main/java exists
            return null;
        }

        // MAVEN MAIN RESOURCES
        String mainResourcesRelPath = bazelPackageFSRelativePath + File.separator + "src"
                + File.separator + "main" + File.separator + "resources";
        File mainResourcesDir = new File(bazelWorkspaceRootPath + File.separator + mainResourcesRelPath);
        if (mainResourcesDir.exists()) {
            result.packageSourceCodeFSPaths.add(mainResourcesRelPath);
        }

        // MAVEN TEST SRC
        String testSrcRelPath = bazelPackageFSRelativePath + File.separator + "src" + File.separator
                + "test" + File.separator + "java";
        File testSrcDir = new File(bazelWorkspaceRootPath + File.separator + testSrcRelPath);
        if (testSrcDir.exists()) {
            result.packageSourceCodeFSPaths.add(testSrcRelPath);
        }
        // MAVEN TEST RESOURCES
        String testResourcesRelPath = bazelPackageFSRelativePath + File.separator + "src"
                + File.separator + "test" + File.separator + "resources";
        File testResourcesDir = new File(bazelWorkspaceRootPath + File.separator + testResourcesRelPath);
        if (testResourcesDir.exists()) {
            result.packageSourceCodeFSPaths.add(testResourcesRelPath);
        }

        return result;
    }

    private static EclipseProjectStructure doExpensiveBazelQueryStructureCheck(BazelPackageLocation packageNode,
            File workspaceRootDir, String bazelWorkspaceRootPath, String bazelPackageFSRelativePath, File packageDir) {
        EclipseProjectStructure result = new EclipseProjectStructure();

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
            // the results will contain paths like this:
            //   source/dev/com/salesforce/foo/Bar.java
            // but it can also have non-java source files, so we need to check for that

            Set<String> alreadySeenBasePaths = new HashSet<>();
            FSTree otherSourcePaths = new FSTree();

            for (String srcPath : results) {
                File sourceFile = new File(packageDir, srcPath);
                if (!sourceFile.exists()) {
                    // this file is coming from a package outside of our package from somewhere else in the
                    // Bazel workspace, so we don't consider it for our project source paths
                    continue;
                }

                // it is expensive to formalize the source path as it involves parsing the source file,
                // so try to bail out here early
                boolean alreadySeen = false;
                for (String alreadySeenBasePath : alreadySeenBasePaths) {
                    if (srcPath.startsWith(alreadySeenBasePath)) {
                        alreadySeen = true;
                        break;
                    }
                }

                if (!alreadySeen) {
                    if (srcPath.endsWith(".java")) {
                        SourcePath srcPathObj = BazelJvmSourceFolderResolver.formalizeSourcePath(packageDir, srcPath);
                        if (srcPathObj != null) {
                            String workspacePathToSourceDirectory =
                                    bazelPackageFSRelativePath + File.separator + srcPathObj.pathToDirectory;
                            result.packageSourceCodeFSPaths.add(workspacePathToSourceDirectory);
                            alreadySeenBasePaths.add(srcPathObj.pathToDirectory);
                            LOG.info("Found source path {} for package {}", srcPathObj.pathToDirectory,
                                bazelPackageFSRelativePath);
                        } else {
                            LOG.info("Could not derive source path from {} for package {}", srcPath,
                                bazelPackageFSRelativePath);
                        }
                    } else {
                        LOG.info("Found file of unknown type for source path from {} for package {}?", srcPath,
                            bazelPackageFSRelativePath);
                        FSTree.addNode(otherSourcePaths, srcPath, File.separator, true);
                    }
                }
            }

            // now figure out a reasonable way to represent the source paths of resource files
            computeResourceDirectories(bazelPackageFSRelativePath, result, otherSourcePaths);

            // NOTE: the order of result.packageSourceCodeFSPaths array is important. Eclipse
            // will honor that order in the project explorer. Because we don't know the proper order
            // based on folder names (e.g. main, test) we just sort alphabetically.
            Collections.sort(result.packageSourceCodeFSPaths);
        } else {
            LOG.info("Did not find any source files for package [{}], ignoring for import.", packageLabel);
            result = null;
        }

        return result;
    }

    private static void computeResourceDirectories(String bazelPackageFSRelativePath, EclipseProjectStructure result,
            FSTree otherSourcePaths) {

        // this is not an exact science, see the FSTree class for the algorithm for identifying directories that would
        // be helpful to add
        List<String> resourceDirectoryPaths = FSTree.computeMeaningfulDirectories(otherSourcePaths, File.separator);
        for (String resourceDirectoryPath : resourceDirectoryPaths) {
            String path = bazelPackageFSRelativePath + File.separator + resourceDirectoryPath;
            result.packageSourceCodeFSPaths.add(path);
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
