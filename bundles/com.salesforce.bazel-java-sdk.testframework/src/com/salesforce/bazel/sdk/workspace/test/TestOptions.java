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
package com.salesforce.bazel.sdk.workspace.test;

import java.util.HashMap;

/**
 * Holder of options that a test has set. Options are used by various elements of the mocking framework to allow you to
 * alter the way the mocks behave. The TestOptions object is passed around the layers and is available to most of the
 * Mock objects.
 * <p>
 * It extends Map<String, String> so you can add arbitrary test data to it. The commonly used options are explicit
 * member variables.
 */
public class TestOptions extends HashMap<String, String> {
    private static final long serialVersionUID = 1L;

    // COMMON OPTIONS

    // short string to be used as a unique identifier (for unique temp folder names, etc)
    // max 6 characters to reduce path length problems
    public String uniqueKey = "gen";

    public TestOptions uniqueKey(String key) {
        uniqueKey = key;
        if (key.length() > 6) {
            uniqueKey = key.substring(0, 5);
        }
        return this;
    }

    // version identifier to use in 'bazel version' commands
    public String bazelVersion = "1.0.0";

    public TestOptions bazelVersion(String version) {
        bazelVersion = version;
        return this;
    }

    // number of packages with Java rules to create in the test workspace
    public int numberOfJavaPackages = 0;

    public TestOptions numberOfJavaPackages(int num) {
        numberOfJavaPackages = num;
        return this;
    }

    // number of packages with genrules to create in the test workspace
    public int numberGenrulePackages = 0;

    public TestOptions numberGenrulePackages(int num) {
        numberGenrulePackages = num;
        return this;
    }

    // compute the classpaths? TODO why not
    public boolean computeClasspaths = false;

    public TestOptions computeClasspaths(boolean compute) {
        computeClasspaths = compute;
        return this;
    }

    // for Java packages, should test deps be listed explicitly
    public boolean explicitJavaTestDeps = true;

    public TestOptions explicitJavaTestDeps(boolean explicit) {
        explicitJavaTestDeps = explicit;
        return this;
    }

    // WORKSPACE and BUILD (if false), or WORKSPACE.bazel and BUILD.bazel (if true)
    public boolean useAltConfigFileNames = false;

    public TestOptions useAltConfigFileNames(boolean alt) {
        useAltConfigFileNames = alt;
        return this;
    }

    // non Maven layouts; if enabled, will use source/dev and source/test
    public boolean nonStandardJavaLayout_enabled = false;

    public TestOptions nonStandardJavaLayout_enabled(boolean non) {
        nonStandardJavaLayout_enabled = non;
        return this;
    }

    // if multiple, will also have source/dev2, source/test2
    public boolean nonStandardJavaLayout_multipledirs = false;

    public TestOptions nonStandardJavaLayout_multipledirs(boolean mult) {
        nonStandardJavaLayout_multipledirs = mult;
        return this;
    }

    // add a java_import rule to the generated java projects
    public boolean addJavaImport = false;

    public TestOptions addJavaImportRule(boolean addImport) {
        addJavaImport = addImport;
        return this;
    }

    // add a java_binary rule to the generated java projects
    public boolean addJavaBinary = false;
    public static final String JAVA_BINARY_TARGET_NAME = "thejavabinary";

    public TestOptions addJavaBinaryRule(boolean addBinary) {
        addJavaBinary = addBinary;
        return this;
    }

    // just throw a random nested workspace in the mix, to test that we ignore it (see bef issue #25)
    // when we start to support nestedWorkspaces, this should default to false and only some tests will
    // test the nested workspace
    public boolean addFakeNestedWorkspace = true;

    public TestOptions addFakeNestedWorkspace(boolean fake) {
        addFakeNestedWorkspace = fake;
        return this;
    }

    // almost all tests should fail if an unknown target (not found in the underlying test workspace) is passed to a command
    // if you are testing failure cases, this can be set to "false" so that a Bazel error is simulated instead
    public boolean failTestForUnknownTarget = true;

    public TestOptions failTestForUnknownTarget(boolean fail) {
        failTestForUnknownTarget = fail;
        return this;
    }

}
