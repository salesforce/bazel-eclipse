package com.salesforce.bazel.eclipse.command.mock.type;

import java.util.List;
import java.util.Map;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

/**
 * Simulates an invocation of a 'bazel version' command.
 */
public class MockVersionCommand extends MockCommand {

    public MockVersionCommand(List<String> commandTokens, Map<String, String> testOptions, TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens);
        
        addSimulatedOutputToCommandStdOut("Build label: 1.0.0", "Build time: Thu Oct 10 10:19:27 2019 (1570702767)",
            "Build timestamp: 1570702767", "Build timestamp as int: 1570702767");    
        
        // TODO add tests for failing if the bazel version is too low
        // TODO add tests for bazel version failing (misconfigured bazel executable path)
    }
}
