package com.salesforce.bazel.eclipse.command.mock.type;

import java.io.File;
import java.util.List;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;

public class MockInfoCommand extends MockCommand {

    public MockInfoCommand(List<String> commandTokens, File bazelWorkspaceRoot, File bazelExecutionRoot, File bazelOutputBase, File bazelBin) {
        super(commandTokens);
        
        if (commandTokens.size() < 3) {
            // this is just the generic 'bazel info', we probably should not be issuing this command from the plugins as there are better ways
            throw new IllegalArgumentException("The plugin issued the command 'bazel info' without a third arg. Please consider using a more specific 'bazel info xyz' command instead.");
        } else if ("workspace".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut("INFO: Invocation ID: a6809b5e-3fb4-462e-8fcc-2c18575122e7", bazelWorkspaceRoot.getAbsolutePath());
        } else if ("execution_root".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(bazelExecutionRoot.getAbsolutePath());
        } else if ("output_base".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(bazelOutputBase.getAbsolutePath());
        } else if ("bazel-bin".equals(commandTokens.get(2))) {
            addSimulatedOutputToCommandStdOut(bazelBin.getAbsolutePath());
        } else {
            throw new IllegalArgumentException("MockCommandBuilder does not know how to mock 'bazel info "+commandTokens.get(2)+"'. Please add code to handle this case.");
        }
    }

}
