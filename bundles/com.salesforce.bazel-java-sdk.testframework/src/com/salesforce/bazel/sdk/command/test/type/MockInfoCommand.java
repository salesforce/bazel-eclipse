package com.salesforce.bazel.sdk.command.test.type;

import java.util.List;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Simulates an info command (bazel info XYZ) where XYZ is one of a set of descriptors supported by Bazel.
 */
public class MockInfoCommand extends MockCommand {

    public MockInfoCommand(List<String> commandTokens, TestOptions testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens, testOptions, testWorkspaceFactory);

        if (commandTokens.size() < 3) {
            // this is just the generic 'bazel info', which is a legal bazel command but it provides a many line response
            // we probably should not be issuing this command from BEF, we should use the more specific forms instead
            throw new IllegalArgumentException(
                    "The plugin issued the command 'bazel info' without a third arg. Please consider using a more specific 'bazel info xyz' command instead.");
        }
        if ("workspace".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut("INFO: Invocation ID: a6809b5e-3fb4-462e-8fcc-2c18575122e7",
                testWorkspaceFactory.workspaceDescriptor.workspaceRootDirectory.getAbsolutePath());
        } else if ("execution_root".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(testWorkspaceFactory.workspaceDescriptor.dirExecRoot.getAbsolutePath());
        } else if ("output_base".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(
                testWorkspaceFactory.workspaceDescriptor.outputBaseDirectory.getAbsolutePath());
        } else if ("output_path".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(testWorkspaceFactory.workspaceDescriptor.dirOutputPath.getAbsolutePath());
        } else if ("bazel-bin".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(testWorkspaceFactory.workspaceDescriptor.dirBazelBin.getAbsolutePath());
        } else {
            throw new IllegalArgumentException("MockInfoCommand does not know how to mock 'bazel info "
                    + commandTokens.get(2) + "'. Please add code to handle this case.");
        }
    }

}
