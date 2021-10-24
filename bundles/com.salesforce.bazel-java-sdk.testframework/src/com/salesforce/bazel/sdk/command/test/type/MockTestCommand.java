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
package com.salesforce.bazel.sdk.command.test.type;

import java.util.Arrays;
import java.util.List;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Simulates the running of a "bazel test //a/b/c" command.
 */
public class MockTestCommand extends MockCommand {

    public MockTestCommand(List<String> commandTokens, TestOptions testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens, testOptions, testWorkspaceFactory);

        if (commandTokens.size() < 3) {
            // this is just 'bazel test' without a target, which is not valid, blow up here as there is something wrong in the calling code
            throw new IllegalArgumentException(
                    "The plugin issued the command 'bazel test' without a third arg. This is not a valid bazel command.");
        }

        // BEF uses a 'bazel test' command to determine the .bazelrc Bazel command options that are set for the workspace.
        // It is an odd pattern, but it is the best way to capture all of the .bazelrc options.
        // This block detects this use case and returns the appropriate response.
        // Note that some low level tests do not use this mechanism to simulate .bazelrc options, see also MockBazelWorkspaceMetadataStrategy.
        if ((commandTokens.size() == 3) && "--announce_rc".equals(commandTokens.get(2))) {
            if (testOptions.explicitJavaTestDeps) {
                addSimulatedOutputToCommandStdErr("   'test' options: --explicit_java_test_deps=true");
            } else {
                addSimulatedOutputToCommandStdErr("   'test' options: --explicit_java_test_deps=false");
            }
            return;
        }

        // proceed with an actual test invocation
        // check that the target (e.g. projects/libs/javalib0) is valid relative to our test workspace
        String target = findBazelTargetInArgs();
        if (!isValidBazelTarget(target)) {
            // by default, isValidBazelTarget() will throw an exception if the package is missing, but the test may configure it to return false instead
            errorLines = Arrays.asList("ERROR: no such package '" + target
                + "': BUILD file not found in any of the following directories. Add a BUILD file to a directory to mark it as a package.",
                "- /fake/path/" + target); // $SLASH_OK: bazel path
        }

        // TODO use testOptions to simulate fail certain tests
        // TODO write out actual test results
    }

}
