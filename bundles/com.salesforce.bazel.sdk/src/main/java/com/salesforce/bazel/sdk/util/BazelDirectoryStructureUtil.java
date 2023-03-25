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
package com.salesforce.bazel.sdk.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains only static methods, that interact with the file system to provide information about bazel
 * artifacts.
 */
public final class BazelDirectoryStructureUtil {

    private static Logger LOG = LoggerFactory.getLogger(BazelDirectoryStructureUtil.class);

    public static List<String> findBazelPackages(File repositoryRoot, String relativeRepositoryPath) {
        var rootPath = repositoryRoot.toPath();
        try {
            return Files.walk(rootPath.resolve(relativeRepositoryPath)).filter(Files::isRegularFile)
                    .filter(p -> BazelConstants.BUILD_FILE_NAMES.contains(p.getFileName().toString()))
                    .map(Path::getParent).map(p -> rootPath.relativize(p).toString()).collect(Collectors.toList());
        } catch (IOException ex) {
            LOG.error("Failed to look for BUILD files at " + relativeRepositoryPath + ": " + ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    public static boolean isBazelPackage(File repositoryRoot, String possiblePackagePath) {
        var rootPath = repositoryRoot.toPath();
        var packagePath = rootPath.resolve(possiblePackagePath);
        for (String buildFileName : BazelConstants.BUILD_FILE_NAMES) {
            var buildFilePath = packagePath.resolve(buildFileName);
            if (Files.isRegularFile(buildFilePath)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWorkspaceRoot(File repositoryRoot) {
        var rootPath = repositoryRoot.toPath();
        for (String buildFileName : BazelConstants.WORKSPACE_FILE_NAMES) {
            var buildFilePath = rootPath.resolve(buildFileName);
            if (Files.isRegularFile(buildFilePath)) {
                return true;
            }
        }
        return false;
    }

    private BazelDirectoryStructureUtil() {
        throw new IllegalStateException("Not meant to be instantiated");
    }
}
