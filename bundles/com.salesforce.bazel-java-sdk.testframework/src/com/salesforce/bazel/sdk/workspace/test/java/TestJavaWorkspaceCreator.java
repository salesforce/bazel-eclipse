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
package com.salesforce.bazel.sdk.workspace.test.java;

import java.io.File;
import java.io.IOException;

import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.workspace.test.TestAspectFileCreator;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;

/**
 * Creates workspace level assets for Java packages. For example, workspace rules to load Maven jars (e.g.
 * maven_install)
 * <p>
 * Warning: in some contexts our tests run in parallel, so make sure to avoid any static variables in this framework
 * otherwise you can have tests writing files into the wrong test workspace.
 */
public class TestJavaWorkspaceCreator {

    public static void createMavenInstallJars(TestBazelWorkspaceDescriptor workspaceDescriptor) throws Exception {
        File outdir = workspaceDescriptor.outputBaseDirectory;

        // slf4j
        TestJarDescriptor slf4jDescriptor =
                createMavenInstallJar(outdir, "org_slf4j_slf4j_api", "org.slf4j", "slf4j-api", "1.7.25");
        slf4jDescriptor.aspectFilePath =
                TestAspectFileCreator.createAspectFileForMavenInstallJar(outdir, slf4jDescriptor);
        workspaceDescriptor.createdExternalJars.add(slf4jDescriptor);

        // guava
        TestJarDescriptor guavaJarDescriptor =
                createMavenInstallJar(outdir, "com_google_guava_guava", "com.google.guava", "guava", "20.0");
        guavaJarDescriptor.aspectFilePath =
                TestAspectFileCreator.createAspectFileForMavenInstallJar(outdir, guavaJarDescriptor);
        workspaceDescriptor.createdExternalJars.add(guavaJarDescriptor);

        // junit
        TestJarDescriptor junitDescriptor = createMavenInstallJar(workspaceDescriptor.outputBaseDirectory,
            "junit_junit", "junit", "junit", "4.12");
        junitDescriptor.aspectFilePath = TestAspectFileCreator
                .createAspectFileForMavenInstallJar(workspaceDescriptor.outputBaseDirectory, junitDescriptor);
        workspaceDescriptor.createdExternalJars.add(junitDescriptor);

        // hamcrest
        TestJarDescriptor hamcrestDescriptor = createMavenInstallJar(workspaceDescriptor.outputBaseDirectory,
            "org_hamcrest_hamcrest_core", "org.hamcrest", "hamcrest-core", "1.3");
        hamcrestDescriptor.aspectFilePath = TestAspectFileCreator
                .createAspectFileForMavenInstallJar(workspaceDescriptor.outputBaseDirectory, hamcrestDescriptor);
        workspaceDescriptor.createdExternalJars.add(hamcrestDescriptor);
    }

    /**
     * Creates the jars (bin, src) on the file system to be used as maven_install deps in java rules
     */
    public static TestJarDescriptor createMavenInstallJar(File dirOutputBase, String bazelName,
            String groupNameWithDots, String artifactName, String version) throws IOException {
        TestJarDescriptor jarDescriptor = new TestJarDescriptor(bazelName); // org_slf4j_slf4j_api
        String combinedName = artifactName + "-" + version;

        jarDescriptor.isMaven = true;
        jarDescriptor.groupNameWithDots = groupNameWithDots;
        jarDescriptor.artifactName = artifactName;
        jarDescriptor.version = version;

        jarDescriptor.jarFileName = combinedName + ".jar";
        String folderPath = groupNameWithDots.replace("\\.", File.separator) + File.separator + artifactName
                + File.separator + version;

        // create the relative dir path from output base
        String maveninstallPath = FSPathHelper.osSeps("external/maven/v1/https/repo1.maven.org/maven2/");
        File jarDir = new File(dirOutputBase, maveninstallPath + folderPath);
        jarDir.mkdirs();

        // create the jar
        jarDescriptor.jarRelativePath =
                FSPathHelper.osSeps(maveninstallPath + folderPath + "/" + jarDescriptor.jarFileName); // $SLASH_OK
        File fakeJar = new File(dirOutputBase, jarDescriptor.jarRelativePath);
        fakeJar.createNewFile();
        jarDescriptor.jarAbsolutePath = fakeJar.getAbsolutePath();
        System.out.println("Created fake jar file: " + fakeJar.getCanonicalPath());

        // create the source jar
        jarDescriptor.srcJarRelativePath =
                FSPathHelper.osSeps(maveninstallPath + folderPath + "/" + combinedName + "-sources.jar"); // $SLASH_OK
        File fakeSourceJar = new File(dirOutputBase, jarDescriptor.srcJarRelativePath);
        fakeSourceJar.createNewFile();
        jarDescriptor.srcJarAbsolutePath = fakeSourceJar.getAbsolutePath();

        return jarDescriptor;
    }

}
