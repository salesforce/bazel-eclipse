package com.salesforce.bazel.eclipse.command.mock.type;

import java.util.List;
import java.util.Map;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

/**
 * Simulates the running of a "bazel test //a/b/c" command.
 */
public class MockTestCommand extends MockCommand {

    public MockTestCommand(List<String> commandTokens, Map<String, String> testOptions, TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens);
        
        if (commandTokens.size() < 3) {
            // this is just 'bazel test' without a target, which is not valid, blow up here as there is something wrong in the calling code
            throw new IllegalArgumentException("The plugin issued the command 'bazel test' without a third arg. This is not a valid bazel command.");
        }
        
        // BEF uses a 'bazel test' command to determine the .bazelrc Bazel command options that are set for the workspace.
        // It is an odd pattern, but it is the best way to capture all of the .bazelrc options.
        // This block detects this use case and returns the appropriate response.
        // Note that some low level tests do not use this mechanism to simulate .bazelrc options, see also MockBazelWorkspaceMetadataStrategy.
        if (commandTokens.size() == 3 && "--announce_rc".equals(commandTokens.get(2))) {
            if ("true".equals(testOptions.get("explicit_java_test_deps"))) {
                addSimulatedOutputToCommandStdErr("   'test' options: --explicit_java_test_deps=true");
            } else {
                addSimulatedOutputToCommandStdErr("   'test' options: --explicit_java_test_deps=false");
            }
        }
        
        // TODO pull more bazelrc options from testOptions
        // TODO use testOptions to simulate fail certain tests
        // TODO fail if passed package is not in test workspace
        // TODO write out actual test results
    }
    
}
