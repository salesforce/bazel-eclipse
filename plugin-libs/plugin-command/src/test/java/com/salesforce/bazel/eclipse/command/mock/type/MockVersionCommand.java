package com.salesforce.bazel.eclipse.command.mock.type;

import java.util.List;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;

public class MockVersionCommand extends MockCommand {

    public MockVersionCommand(List<String> commandTokens) {
        super(commandTokens);
        
        addSimulatedOutputToCommandStdOut("Build label: 1.0.0", "Build time: Thu Oct 10 10:19:27 2019 (1570702767)",
            "Build timestamp: 1570702767", "Build timestamp as int: 1570702767");    
    }
}
