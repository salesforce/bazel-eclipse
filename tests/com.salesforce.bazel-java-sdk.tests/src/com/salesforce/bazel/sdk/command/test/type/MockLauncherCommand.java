package com.salesforce.bazel.sdk.command.test.type;

import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.command.test.MockCommandSimulatedOutput;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;

/**
 * Simulates an invocation of a launcher script (bazel run //a/b/c) from Bazel
 * <p>
 * Since launchers are by definition use case dependent, we expect the test to provide the simulated output lines.
 */
public class MockLauncherCommand extends MockCommand {

    /**
     * Test must provide a set of simulatedOutputLinesProvidedByTest
     */
    public MockLauncherCommand(List<String> commandTokens, Map<String, String> testOptions,
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
                    "The MockLauncherCommand does not have enough output to provide to simulate the launcher commands. There are "
                            + simulatedOutputLinesProvidedByTest.size() + " outputs configured. Command as received:\n"
                            + commandPretty);
        }
    }
}
