package com.salesforce.bazel.sdk.command.test.type;

import java.util.List;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.command.test.MockCommandSimulatedOutput;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * You may want to run a test that invokes a command not already supported by specific code. This is a catch all mock
 * command. This is mostly just used to test the low level command running infra.
 */
public class MockCustomCommand extends MockCommand {

    /**
     * Test must provide a set of simulatedOutputLinesProvidedByTest
     */
    public MockCustomCommand(List<String> commandTokens, TestOptions testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory,
            List<MockCommandSimulatedOutput> simulatedOutputLinesProvidedByTest) {
        super(commandTokens, testOptions, testWorkspaceFactory);

        boolean handled = false;
        for (MockCommandSimulatedOutput candidateOutput : simulatedOutputLinesProvidedByTest) {
            if (candidateOutput.doesMatch(commandTokens)) {
                // the output is targeted to this command
                outputLines = candidateOutput.outputLines;
                errorLines = candidateOutput.errorLines;
                handled = true;
                break;
            }
        }

        if (!handled) {
            String commandPretty = "";
            for (String token : commandTokens) {
                commandPretty = commandPretty + token + " ";
            }
            throw new IllegalStateException(
                    "The MockCustomCommand does not have enough output to provide to simulate the Bazel command. There are "
                            + simulatedOutputLinesProvidedByTest.size() + " outputs configured. Command as received:\n"
                            + commandPretty);
        }

    }
}
