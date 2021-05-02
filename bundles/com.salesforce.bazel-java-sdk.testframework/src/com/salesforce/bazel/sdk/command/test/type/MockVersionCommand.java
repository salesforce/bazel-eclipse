package com.salesforce.bazel.sdk.command.test.type;

import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Simulates an invocation of a 'bazel version' command.
 */
public class MockVersionCommand extends MockCommand {

    public static final String TESTOPTION_BAZELVERSION = "TESTOPTION_BAZELVERSION";
    static {
        TestOptions.advertise(TESTOPTION_BAZELVERSION);
    }

    public MockVersionCommand(List<String> commandTokens, Map<String, String> testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens, testOptions, testWorkspaceFactory);

        // 1.0.0 is the minimum supported Bazel version currently, tests can tell this mock to return a different value
        String bazelVersion = testOptions.getOrDefault(TESTOPTION_BAZELVERSION, "1.0.0");

        addSimulatedOutputToCommandStdOut("Build label: " + bazelVersion,
            "Build time: Thu Oct 10 10:19:27 2019 (1570702767)", "Build timestamp: 1570702767",
            "Build timestamp as int: 1570702767");
    }
}
