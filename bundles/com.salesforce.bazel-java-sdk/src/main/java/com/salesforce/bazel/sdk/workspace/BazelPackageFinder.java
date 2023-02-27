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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.util.BazelConstants;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Scanner for a Bazel workspace to locate BUILD files that contain rules that are supported by the SDK.
 */
public class BazelPackageFinder {
    private static Logger LOG = LoggerFactory.getLogger(BazelPackageFinder.class);

    public BazelPackageFinder() {}

    public void findBuildFileLocations(File dir, WorkProgressMonitor monitor, Set<File> buildFileLocations, int depth) {
        if (!dir.isDirectory()) {
            return;
        }

        try {

            // collect all BUILD files
            List<Path> buildFiles = new ArrayList<>(1000);

            Path start = dir.toPath();
            Files.walkFileTree(start, new FileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (start.relativize(dir).toString().startsWith("bazel-")) {
                        // this is a Bazel internal directory at the root of the project dir, ignore
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (dir.getFileName().toString().equals("target")) {
                        // skip Maven target directories
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (dir.getFileName().toString().equals(".bazel")) {
                        // skip Core .bazel directory
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    // ignore nested workspaces until we work on BEF issue #25
                    if (dir.compareTo(start) != 0) {
                        File directory = dir.toFile();
                        for (String candidate : BazelConstants.WORKSPACE_FILE_NAMES) {
                            File candidateWorkspaceFile = new File(directory, candidate);
                            if (candidateWorkspaceFile.exists()) {
                                LOG.info(
                                    "Skipping Bazel workspace path {} because we do not support nested workspaces yet.",
                                    directory.getAbsolutePath());
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isBuildFile(file)) {
                        buildFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

            });

            // scan all build files
            // normally in the SDK we do not use Java streams, to make the code more accessible, but the parallel
            // streaming here really speeds up the file system scan
            Set<File> syncSet = Collections.synchronizedSet(buildFileLocations);
            buildFiles.parallelStream().forEach(file -> {
                // great, this dir is a Bazel package (but this may be a non-Java package)
                // scan the BUILD file looking for java rules, only add if this is a java project
                if (BuildFileSupport.hasRegisteredRules(file.toFile())) {
                    syncSet.add(FSPathHelper.getCanonicalFileSafely(file.getParent().toFile()));
                }
            });

        } catch (Exception anyE) {
            LOG.error("ERROR scanning for Bazel packages: {}", anyE.getMessage());
        }
    }

    private static boolean isBuildFile(Path candidate) {
        return BazelConstants.BUILD_FILE_NAMES.contains(candidate.getFileName().toString());
    }

    public Set<File> findBuildFileLocations(File rootDirectoryFile) throws IOException {
        Set<File> files = ConcurrentHashMap.newKeySet();
        findBuildFileLocations(rootDirectoryFile, null, files, 0);
        return files;
    }
}
