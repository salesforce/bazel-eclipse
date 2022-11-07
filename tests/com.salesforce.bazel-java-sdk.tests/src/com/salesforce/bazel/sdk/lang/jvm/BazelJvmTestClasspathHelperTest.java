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
 */
package com.salesforce.bazel.sdk.lang.jvm;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.TreeSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.lang.jvm.classpath.BazelJvmTestClasspathHelper;
import com.salesforce.bazel.sdk.path.FSPathHelper;

public class BazelJvmTestClasspathHelperTest {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    BazelJvmTestClasspathHelper bazelJvmTestClasspathHelper = new BazelJvmTestClasspathHelper();

    @Test
    public void getPathsToJars() {
        List<String> result =
                bazelJvmTestClasspathHelper.getClasspathJarsFromParamsFile(new Scanner(PARAM_FILE_CONTENTS));

        String deployPath = FSPathHelper.osSeps(
                "bazel-out/darwin-fastbuild/bin/projects/libs/banana/banana-api/src/test/java/demo/banana/api/BananaTest_deploy.jar"); // $SLASH_OK
        String libPath =
                FSPathHelper.osSeps("bazel-out/darwin-fastbuild/bin/projects/libs/banana/banana-api/libbanana-api.jar"); // $SLASH_OK
        String slf4jPath = FSPathHelper.osSeps("external/org_slf4j_slf4j_api/jar/slf4j-api-1.7.25.jar"); // $SLASH_OK
        String guavaPath = FSPathHelper.osSeps("external/com_google_guava_guava/jar/guava-20.0.jar"); // $SLASH_OK
        String junitPath = FSPathHelper.osSeps("external/junit_junit/jar/junit-4.12.jar"); // $SLASH_OK
        String runnerPath = FSPathHelper.osSeps("external/remote_java_tools/java_tools/Runner_deploy.jar"); // $SLASH_OK
        String testPath = FSPathHelper.osSeps(
                "bazel-out/darwin-fastbuild/bin/projects/libs/banana/banana-api/src/test/java/demo/banana/api/BananaTest.jar"); // $SLASH_OK

        assertEquals(7, result.size());
        assertEquals(deployPath, result.get(0));
        assertEquals(libPath, result.get(1));
        assertEquals(slf4jPath, result.get(2));
        assertEquals(guavaPath, result.get(3));
        assertEquals(junitPath, result.get(4));
        assertEquals(runnerPath, result.get(5));
        assertEquals(testPath, result.get(6));
    }

    @Test
    public void getParamsJarSuffix() {
        assertEquals("_deploy.jar-0.params", bazelJvmTestClasspathHelper.getParamsJarSuffix(false));
        assertEquals("_deploy-src.jar-0.params", bazelJvmTestClasspathHelper.getParamsJarSuffix(true));
    }

    @Test
    public void testClasspathAggregation() throws Exception {
        BazelJvmTestClasspathHelper.ParamFileResult result = new BazelJvmTestClasspathHelper.ParamFileResult();
        result.paramFiles = new ArrayList<>();
        result.unrunnableLabels = new TreeSet<>();
        File tdir = tmpDir.newFolder("paramFiles");
        File pdir = new File(tdir, "paramFiles");
        pdir.mkdir();
        File execRootDir = new File(tdir, "execroot");
        execRootDir.mkdir();

        // make the param files
        for (int i = 0; i < 10; i++) {
            result.paramFiles.add(createParamFile(pdir, i));
        }

        boolean includeDeployJars = true;
        List<String> jarPaths =
                bazelJvmTestClasspathHelper.aggregateJarFilesFromParamFiles(result.paramFiles, includeDeployJars);
        assertEquals(7, jarPaths.size());

        includeDeployJars = false;
        jarPaths = bazelJvmTestClasspathHelper.aggregateJarFilesFromParamFiles(result.paramFiles, includeDeployJars);
        assertEquals(5, jarPaths.size());
    }

    private static File createParamFile(File dir, int index) {
        String contents = BazelJvmTestClasspathHelperTest.PARAM_FILE_CONTENTS;

        File paramFile = new File(dir, "paramfile" + index);
        try (PrintStream out = new PrintStream(new FileOutputStream(paramFile))) {
            out.print(contents.toString());
        } catch (Exception anyE) {
            anyE.printStackTrace();
        }
        return paramFile;

    }

    public static String PARAM_FILE_CONTENTS = "--output\n" + FSPathHelper.osSeps(
            "bazel-out/darwin-fastbuild/bin/projects/libs/banana/banana-api/src/test/java/demo/banana/api/BananaTest_deploy.jar\n") // $SLASH_OK
    + "--compression\n" + "--normalize\n" + "--main_class\n"
    + "com.google.testing.junit.runner.BazelTestRunner\n" + "--build_info_file\n"
    + FSPathHelper.osSeps("bazel-out/darwin-fastbuild/include/build-info-redacted.properties\n") // $SLASH_OK
    + "--sources\n"
    + FSPathHelper.osSeps("bazel-out/darwin-fastbuild/bin/projects/libs/banana/banana-api/libbanana-api.jar") // $SLASH_OK
    + ",//projects/libs/banana/banana-api:banana-api\n"
    + FSPathHelper.osSeps("external/org_slf4j_slf4j_api/jar/slf4j-api-1.7.25.jar") // $SLASH_OK
    + ",@@maven//:org_slf4j_slf4j_api:slf4j-api-1.7.25.jar\n"
    + FSPathHelper.osSeps("external/com_google_guava_guava/jar/guava-20.0.jar") // $SLASH_OK
    + ",@@maven//:com_google_guava_guava:guava-20.0.jar\n"
    + FSPathHelper.osSeps("external/junit_junit/jar/junit-4.12.jar") + ",@@maven//:junit_junit:junit-4.12.jar\n" // $SLASH_OK
    + FSPathHelper.osSeps("external/remote_java_tools/java_tools/Runner_deploy.jar") // $SLASH_OK
    + ",@@remote_java_tools//:java_tools/Runner_deploy.jar\n"
    + FSPathHelper.osSeps(
            "bazel-out/darwin-fastbuild/bin/projects/libs/banana/banana-api/src/test/java/demo/banana/api/BananaTest.jar") // $SLASH_OK
    + ",//projects/libs/banana/banana-api:src/test/java/demo/banana/api/BananaTest\n" + "";
}
