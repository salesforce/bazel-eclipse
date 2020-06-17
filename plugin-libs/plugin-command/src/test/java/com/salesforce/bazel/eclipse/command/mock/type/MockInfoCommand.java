package com.salesforce.bazel.eclipse.command.mock.type;

import java.util.List;
import java.util.Map;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

/**
 * Simulates an info command (bazel info XYZ) where XYZ is one of a set of descriptors supported by Bazel.
 */
public class MockInfoCommand extends MockCommand {

    public MockInfoCommand(List<String> commandTokens, Map<String, String> testOptions, TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens);
        
        if (commandTokens.size() < 3) {
            // this is just the generic 'bazel info', which is a legal bazel command but it provides a many line response
            // we probably should not be issuing this command from BEF, we should use the more specific forms instead
            throw new IllegalArgumentException("The plugin issued the command 'bazel info' without a third arg. Please consider using a more specific 'bazel info xyz' command instead.");
        } else if ("workspace".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut("INFO: Invocation ID: a6809b5e-3fb4-462e-8fcc-2c18575122e7", testWorkspaceFactory.workspaceDescriptor.workspaceRootDirectory.getAbsolutePath());
        } else if ("execution_root".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(testWorkspaceFactory.workspaceDescriptor.dirExecRoot.getAbsolutePath());
        } else if ("output_base".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(testWorkspaceFactory.workspaceDescriptor.dirOutputPath.getAbsolutePath());
        } else if ("bazel-bin".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(testWorkspaceFactory.workspaceDescriptor.dirBazelBin.getAbsolutePath());
        } else {
            throw new IllegalArgumentException("MockInfoCommand does not know how to mock 'bazel info "+commandTokens.get(2)+"'. Please add code to handle this case.");
        }
    }

}
