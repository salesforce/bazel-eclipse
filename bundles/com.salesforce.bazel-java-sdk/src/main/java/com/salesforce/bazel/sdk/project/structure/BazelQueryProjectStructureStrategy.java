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
package com.salesforce.bazel.sdk.project.structure;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.path.FSTree;
import com.salesforce.bazel.sdk.path.SourcePathSplitterStrategy;
import com.salesforce.bazel.sdk.path.SplitSourcePath;

/**
 * ProjectStructureStrategy that locates the source directories in a project by using Bazel Query. This is an expensive
 * strategy, so is used as the last resort.
 */
public class BazelQueryProjectStructureStrategy extends ProjectStructureStrategy {
    private static final LogHelper LOG = LogHelper.log(BazelQueryProjectStructureStrategy.class);

    /**
     * Test source files will generally appear in a directory hierarchy that contains a directory named "test" or
     * "tests". If we can rely on this, this is a major optimization because we don't need to run lots of queries to
     * figure out what source files are only used by tests.
     * <p>
     * This is public so that tools can update this set as needed.
     */
    public static Set<String> testSourceCodeFolderMarkers = new HashSet<>();
    static {
        testSourceCodeFolderMarkers.add("test");
        testSourceCodeFolderMarkers.add("tests");
    }

    @Override
    public ProjectStructure doStructureAnalysis(BazelWorkspace bazelWorkspace, BazelPackageLocation packageNode,
            BazelWorkspaceCommandRunner commandRunner) {
        ProjectStructure result = new ProjectStructure();

        File workspaceRootDir = bazelWorkspace.getBazelWorkspaceRootDirectory();
        String packageRelPath = packageNode.getBazelPackageFSRelativePath();
        File packageDir = new File(workspaceRootDir, packageRelPath); // TODO move this to the PackageLocation api
        result.projectPath = packageDir;

        BazelLabel packageLabel = new BazelLabel(packageRelPath, BazelLabel.BAZEL_WILDCARD_ALLTARGETS_STAR);

        // execute the expensive query, this will take a few seconds to run at least
        Collection<String> results = runBazelQueryForSourceFiles(workspaceRootDir, packageLabel, commandRunner);

        if ((results != null) && (results.size() > 0)) {
            // the results will contain paths like this:
            //   source/dev/com/salesforce/foo/Bar.java
            // but it can also have non-java source files, so we need to check for that

            Set<String> alreadySeenBasePaths = new HashSet<>();
            FSTree resourceFileStructure = new FSTree();

            for (String srcPath : results) {
                File sourceFile = new File(packageDir, srcPath);
                if (!sourceFile.exists()) {
                    // this file is coming from a package outside of our package from somewhere else in the
                    // Bazel workspace, so we don't consider it for our project source paths
                    continue;
                }

                // it is expensive to formalize the source path as it involves parsing the source file,
                // so try to bail out here early if we have already seen the directory this source file is in
                boolean alreadySeen = false;
                for (String alreadySeenBasePath : alreadySeenBasePaths) {
                    if (srcPath.startsWith(alreadySeenBasePath)) {
                        alreadySeen = true;
                        break;
                    }
                }

                if (!alreadySeen) {
                    if (srcPath.endsWith(".java")) {
                        // splits the path into two segments:
                        // 1) projects/libs/foo/src/main/java  2) com/salesforce/foo/Foo.java
                        SplitSourcePath srcPathObj = splitSourcePath(packageDir, srcPath);

                        if (srcPathObj != null) {
                            String packageRelPathToFile =
                                    packageRelPath + File.separator + srcPathObj.sourceDirectoryPath;

                            boolean isTestPath = FSPathHelper.doesPathContainNamedResource(
                                srcPathObj.sourceDirectoryPath, testSourceCodeFolderMarkers);
                            if (isTestPath) {
                                result.testSourceDirFSPaths.add(packageRelPathToFile);
                            } else {
                                result.mainSourceDirFSPaths.add(packageRelPathToFile);
                            }
                            alreadySeenBasePaths.add(srcPathObj.sourceDirectoryPath);
                            LOG.info("Found source path {} for package {}", srcPathObj.sourceDirectoryPath,
                                packageRelPath);
                        } else {
                            // the path could not be split for some reason
                            LOG.info("Could not derive source path from {} for package {}", srcPath,
                                packageRelPath);
                        }
                    } else {
                        // this is a resource file, like xyz.properties or abc.xml
                        LOG.info("Found resource file with source path {} for package {}", srcPath,
                            packageRelPath);
                        FSTree.addNode(resourceFileStructure, srcPath, File.separator, true);
                    }
                }
            }

            // now figure out a reasonable way to represent the source paths of resource files
            computeResourceDirectories(packageRelPath, result, resourceFileStructure);

            // NOTE: the order of source paths in the lists is important. Eclipse
            // will honor that order in the project explorer. Because we don't know the proper order
            // based on folder names (e.g. main, test) we just sort alphabetically.
            Collections.sort(result.mainSourceDirFSPaths);
            Collections.sort(result.testSourceDirFSPaths);
        } else {
            LOG.info("Did not find any source files for package [{}], ignoring for import.", packageLabel);
            result = null;
        }

        return result;
    }

    // INTERNALS

    protected Collection<String> runBazelQueryForSourceFiles(File workspaceRootDir, BazelLabel packageLabel,
            BazelWorkspaceCommandRunner commandRunner) {
        Collection<String> results = null;
        try {
            results = commandRunner.querySourceFilesForTarget(workspaceRootDir, packageLabel);
        } catch (Exception anyE) {
            LOG.error("Failed querying package [{}] for source files.", anyE, packageLabel);
        }
        return results;
    }

    protected SplitSourcePath splitSourcePath(File packageDir, String srcPath) {
        SplitSourcePath result = null;
        SourcePathSplitterStrategy splitter = SourcePathSplitterStrategy.getSplitterForFilePath(srcPath);
        if (splitter != null) {
            result = splitter.splitSourcePath(packageDir, srcPath);
        }

        return result;
    }


    private static void computeResourceDirectories(String bazelPackageFSRelativePath, ProjectStructure result,
            FSTree otherSourcePaths) {

        // this is not an exact science, see the FSTree class for the algorithm for identifying directories that would
        // be helpful to add
        List<String> resourceDirectoryPaths = FSTree.computeMeaningfulDirectories(otherSourcePaths, File.separator);
        for (String resourceDirectoryPath : resourceDirectoryPaths) {
            String path = bazelPackageFSRelativePath + File.separator + resourceDirectoryPath;
            result.mainSourceDirFSPaths.add(path);
        }
    }

}
