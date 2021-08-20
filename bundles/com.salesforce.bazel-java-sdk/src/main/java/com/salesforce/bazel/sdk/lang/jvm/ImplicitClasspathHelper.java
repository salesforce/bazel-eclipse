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
package com.salesforce.bazel.sdk.lang.jvm;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Bazel generally requires BUILD file authors to list all dependencies explicitly. However, there are a few legacy
 * cases in which dependencies are implied. For example, java_test implicitly brings in junit, hamcrest,
 * javax.annotation libraries.
 * <p>
 * This is unfortunate because external tools that need to construct the dependency graph (ahem, that's us) we need to
 * know to append the implicit dependencies to the explicit ones identified by the Aspect.
 * <p>
 * This is a helper class for computing implicit dependencies. See https://github.com/salesforce/bazel-eclipse/issues/43
 * for details and design considerations for this class.
 * <p>
 * This code is isolated from the classpath container code because this is somewhat of a hack and it is nice to have it
 * isolated.
 */
public class ImplicitClasspathHelper {

    // observed location where the TestRunner is written; this is an internal Bazel detail that may change
    private static final String IMPLICIT_RUNNER = "external/bazel_tools/tools/jdk/_ijar/TestRunner"; // $SLASH_OK

    public Set<JvmClasspathEntry> computeImplicitDependencies(BazelWorkspace bazelWorkspace,
            AspectTargetInfo targetInfo) {
        Set<JvmClasspathEntry> deps = new HashSet<>();

        String ruleKind = targetInfo.getKind();
        if (!"java_test".equals(ruleKind)) {
            deps = new TreeSet<>();
        }

        // java_test targets do not have implicit deps if .bazelrc has --explicit_java_test_deps=true
        BazelWorkspaceCommandOptions commandOptions = bazelWorkspace.getBazelWorkspaceCommandOptions();
        String explicitDepsOption = commandOptions.getOption("explicit_java_test_deps");
        if ("true".equals(explicitDepsOption)) {
            // the workspace is configured to disallow implicit deps (hooray) so we can bail now
            return deps;
        }

        // HAMCREST, JUNIT, JAVAX.ANNOTATION
        // These implicit deps are leaked into the classpath by the java_test test runner.
        // To faithfully declare the classpath for Eclipse JDT, we ultimately we need to get this jar onto the JDT classpath:
        //     bazel-bin/external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_darwin/java_tools/Runner_deploy-ijar.jar
        // which comes in from the transitive graph (not sure how the toolchain points to the TestRunner though):
        // java_test => @bazel_tools//tools/jdk:current_java_toolchain => @remote_java_tools_darwin//:toolchain  ?=> TestRunner
        String filePathForRunnerJar = computeFilePathForRunnerJar(bazelWorkspace, targetInfo);
        if (filePathForRunnerJar != null) {
            // now manufacture the classpath entry
            boolean isTestLib = true;
            JvmClasspathEntry runnerJarEntry = new JvmClasspathEntry(filePathForRunnerJar, isTestLib);
            deps.add(runnerJarEntry);
        }
        return deps;
    }

    String computeFilePathForRunnerJar(BazelWorkspace bazelWorkspace, AspectTargetInfo targetInfo) {
        File bazelBinDir = bazelWorkspace.getBazelBinDirectory();
        File testRunnerDir = new File(bazelBinDir, FSPathHelper.osSeps(IMPLICIT_RUNNER));

        LogHelper logger = LogHelper.log(this.getClass());
        if (!testRunnerDir.exists()) {
            logger.error("Could not add implicit test deps to target [" + targetInfo.getLabelPath() + "], directory ["
                    + FSPathHelper.getCanonicalPathStringSafely(testRunnerDir) + "] does not exist.");
            return null;
        }
        File runnerJar = findTestRunnerFolder(testRunnerDir);
        return FSPathHelper.getCanonicalPathStringSafely(runnerJar);
    }

    private File findTestRunnerFolder(File testRunnerDir) {
        try {
            return Files.find(testRunnerDir.toPath(), 5,
                (path, attr) -> String.valueOf(path).endsWith("Runner_deploy-ijar.jar"), FileVisitOption.FOLLOW_LINKS)
                    .findFirst().map(Path::toFile).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
