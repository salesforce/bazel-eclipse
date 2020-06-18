package com.salesforce.bazel.eclipse.command.mock.type;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.eclipse.command.mock.MockCommand;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

/**
 * Simulates an invocation of 'bazel query xyz'
 */
public class MockQueryCommand extends MockCommand {

    public MockQueryCommand(List<String> commandTokens, Map<String, String> testOptions, TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens, testOptions, testWorkspaceFactory);
        
        if (commandTokens.size() < 3) {
            // this is just 'bazel build' without a target, which is not valid, blow up here as there is something wrong in the calling code
            throw new IllegalArgumentException("The plugin issued the command 'bazel query' without a third arg. This is not a valid bazel command.");
        }

        // determine the type of query
        String queryArg = commandTokens.get(2);
        if (queryArg.startsWith("kind(rule, //")) {
            // the only bazel query command we currently invoke has this idiom in the third arg:
            // kind(rule, //projects/libs/javalib0:*)
            // strip target to be just '//projects/libs/javalib0:*'
            String target = queryArg.substring(13, queryArg.length()-1);
            
            if (!isValidBazelTarget(target)) {
                errorLines = Arrays.asList(new String[] { "ERROR: no such package '"+target+"': BUILD file not found in any of the following directories. Add a BUILD file to a directory to mark it as a package.", 
                        "- /fake/abs/path/"+target });
                return;
            }
            // TODO create simulated query results in a more dynamic way using the test workspace structure
            if (commandTokens.get(2).contains("javalib0")) {
                addSimulatedOutputToCommandStdOut("java_test rule //projects/libs/javalib0:javalib0Test");
                addSimulatedOutputToCommandStdOut("java_library rule //projects/libs/javalib0:javalib0");
            } else if (commandTokens.get(2).contains("javalib1")) {
                addSimulatedOutputToCommandStdOut("java_test rule //projects/libs/javalib1:javalib1Test");
                addSimulatedOutputToCommandStdOut("java_library rule //projects/libs/javalib1:javalib1");
            }
            
        } else {
            throw new IllegalArgumentException("The plugin issued the command 'bazel query' with an unknown type of query. "+
                    "The mocking layer (MockQueryCommand) does not know how to simulate a response.");
        }
        
    }
}
