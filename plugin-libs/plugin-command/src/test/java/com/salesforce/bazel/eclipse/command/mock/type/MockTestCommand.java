package com.salesforce.bazel.eclipse.command.mock.type;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;

public class MockTestCommand extends MockCommand {
    boolean valid = false;

    public MockTestCommand(List<String> commandTokens, Map<String, String> commandOptions, File bazelWorkspaceRoot, File bazelExecutionRoot, 
            File bazelOutputBase, File bazelBin) {
        super(commandTokens);
        
        if (commandTokens.size() < 3) {
            // this is just 'bazel test' without a target, which is not valid, blow up here as there is something wrong in the calling code
            throw new IllegalArgumentException("The plugin issued the command 'bazel test' without a third arg. This is not a valid bazel command.");
        }
        if (commandTokens.size() == 3 && "--announce_rc".equals(commandTokens.get(2))) {
            if ("true".equals(commandOptions.get("explicit_java_test_deps"))) {
                addSimulatedOutputToCommandStdErr("   'test' options: --explicit_java_test_deps=true");
            } else {
                addSimulatedOutputToCommandStdErr("   'test' options: --explicit_java_test_deps=false");
            }
            valid = true;
        }

    }
    
    public boolean isValid() {
        // TODO!!!!
        return valid;
    }


}
