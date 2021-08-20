package com.salesforce.bazel.sdk.command.test.type;

import java.util.List;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Simulates a 'bazel clean' command. BEF doesn't do much of interest with clean, so this is just a basic no-op style
 * sim.
 */
public class MockCleanCommand extends MockCommand {

    public MockCleanCommand(List<String> commandTokens, TestOptions testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens, testOptions, testWorkspaceFactory);

        addSimulatedOutputToCommandStdOut("INFO: Starting clean.");
    }
}
