package com.salesforce.bazel.sdk.command.test.type;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Simulates the running of a "bazel test //a/b/c" command.
 */
public class MockTestCommand extends MockCommand {

    public static final String TESTOPTION_EXPLICIT_JAVA_TEST_DEPS = "EXPLICIT_JAVA_TEST_DEPS"; // search code base for this string, there are a few 
    static {
        TestOptions.advertise(TESTOPTION_EXPLICIT_JAVA_TEST_DEPS);
    }

    public MockTestCommand(List<String> commandTokens, Map<String, String> testOptions,
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
        if (commandTokens.size() == 3 && "--announce_rc".equals(commandTokens.get(2))) {
            if ("true".equals(testOptions.get(TESTOPTION_EXPLICIT_JAVA_TEST_DEPS))) {
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
            errorLines = Arrays.asList(new String[] { "ERROR: no such package '" + target
                    + "': BUILD file not found in any of the following directories. Add a BUILD file to a directory to mark it as a package.",
                    "- /fake/path/" + target });
            return;
        }

        // TODO use testOptions to simulate fail certain tests
        // TODO write out actual test results
    }

}
