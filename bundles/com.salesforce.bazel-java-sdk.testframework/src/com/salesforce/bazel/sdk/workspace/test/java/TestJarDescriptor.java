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

/**
 * Basket of values that describe a generated test jar file. This is a simple value object that supports jars that are
 * brought into the workspace maven_install, java_import, and other ways.
 */
public class TestJarDescriptor implements Comparable<TestJarDescriptor> {
    public String bazelLabel; // org_slf4j_slf4j_api or //projects/lib/apple:apple-lib

    public String jarFileName; // slf4j-api-1.7.25.jar or libapple.jar
    public String srcJarFileName; // slf4j-api-1.7.25-sources.jar or libapple-src.jar

    public String jarAbsolutePath;
    public String jarRelativePath; // execroot/bazel_demo_simplejava_mvninstall/bazel-out/darwin-fastbuild/bin/projects/services/fruit-salad-service/fruit-salad/fruit-salad.jar

    public String srcJarAbsolutePath;
    public String srcJarRelativePath; // execroot/bazel_demo_simplejava_mvninstall/bazel-out/darwin-fastbuild/bin/projects/services/fruit-salad-service/fruit-salad/fruit-salad-src.jar

    // MAVEN
    // for Maven jars, we have GAV info
    public boolean isMaven = false;
    public String groupNameWithDots; // org.slf4j
    public String artifactName; // slf4j-api
    public String version; // 1.7.25

    // INTERNAL JARS
    // for workspace built jars, Bazel will generate these extra jars
    public String interfaceJarAbsolutePath; // libapple-hjar.jar
    public String testJarAbsolutePath; // apple-test.jar
    public String srcTestJarAbsolutePath; // apple-test-src.jar

    // EXTERNAL JARS (maven_install, jvm_import)
    public String aspectFilePath;

    public TestJarDescriptor(String bazelLabel) {
        this.bazelLabel = bazelLabel;
    }

    @Override
    public int compareTo(TestJarDescriptor other) {
        return bazelLabel.compareTo(other.bazelLabel);
    }
}