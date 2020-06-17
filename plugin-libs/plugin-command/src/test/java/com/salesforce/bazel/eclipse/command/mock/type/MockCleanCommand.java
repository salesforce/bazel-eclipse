package com.salesforce.bazel.eclipse.command.mock.type;

import java.util.List;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;

public class MockCleanCommand extends MockCommand {

    public MockCleanCommand(List<String> commandTokens) {
        super(commandTokens);
        
        addSimulatedOutputToCommandStdOut("INFO: Starting clean.");
    }
}
