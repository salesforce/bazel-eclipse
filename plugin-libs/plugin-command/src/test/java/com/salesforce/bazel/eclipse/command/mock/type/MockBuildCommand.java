package com.salesforce.bazel.eclipse.command.mock.type;

import java.io.File;
import java.util.List;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;

public class MockBuildCommand extends MockCommand {

    public MockBuildCommand(List<String> commandTokens, File bazelWorkspaceRoot, File bazelExecutionRoot, File bazelOutputBase, File bazelBin) {
        super(commandTokens);
        
        if (commandTokens.size() < 3) {
            // this is just 'bazel build' without a target, which is not valid, blow up here as there is something wrong in the calling code
            throw new IllegalArgumentException("The plugin issued the command 'bazel build' without a third arg. This is not a valid bazel command.");
        }

    }
    
    public boolean isValid() {
        // TODO!!!!
        return false;
    }


}
