package com.salesforce.bazel.sdk.command.test.type;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.workspace.test.TestBazelPackageDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelTargetDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;

/**
 * Simulates an invocation of 'bazel query xyz'
 */
public class MockQueryCommand extends MockCommand {

    public MockQueryCommand(List<String> commandTokens, Map<String, String> testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens, testOptions, testWorkspaceFactory);

        if (commandTokens.size() < 3) {
            // this is just 'bazel build' without a target, which is not valid, blow up here as there is something wrong in the calling code
            throw new IllegalArgumentException(
                    "The plugin issued the command 'bazel query' without a third arg. This is not a valid bazel command.");
        }

        // determine the type of query
        String queryArg = commandTokens.get(2);

        if (queryArg.startsWith("kind(rule, set(//")) {
            // QUERY:
            //    kind(rule, set(//projects/libs/javalib0:*))
            // RESPONSE: for each target in the package, a line is written to stdout such as:
            //    java_library rule //projects/libs/javalib0:javalib0
            //    java_test rule //projects/libs/javalib0:javalib0Test

            // strip target to be just '//projects/libs/javalib0'
            String queryPackage = queryArg.substring(17, queryArg.length() - 4);

            if (!isValidBazelTarget(queryPackage)) {
                // by default, isValidBazelTarget() will throw an exception if the package is missing, but the test may configure it to return false instead
                errorLines = Arrays.asList("ERROR: no such package '" + queryPackage
                    + "': BUILD file not found in any of the following directories. Add a BUILD file to a directory to mark it as a package.", "- /fake/abs/path/" + queryPackage); // $SLASH_OK: bazel path
                return;
            }

            TestBazelPackageDescriptor queryPackageDescriptor =
                    testWorkspaceFactory.workspaceDescriptor.createdPackages.get(queryPackage);
            if (queryPackageDescriptor == null) {
                throw new IllegalStateException("The mock package descriptor is missing for package [" + queryPackage
                    + "]. This is a bug in the mock testing framework.");
            }

            // the query is for :* which means all targets, so iterate through the package's targets and write a line per target to stdout
            for (TestBazelTargetDescriptor target : queryPackageDescriptor.targets.values()) {
                String outputString = target.targetType + " rule //" + target.targetPath;
                addSimulatedOutputToCommandStdOut(outputString);
            }
        } else if (queryArg.startsWith("kind('source file', deps")) {
            // QUERY:
            //    kind('source file', deps(//:*))
            // TODO simluate source file queries
            System.err.println(
                    "Test framework (MockQueryCommand) is not simulating the response of Bazel query for source files yet. Returning no output.");
        } else {
            throw new IllegalArgumentException(
                "The plugin issued the command 'bazel query' with an unknown type of query. "
                        + "The mocking layer (MockQueryCommand) does not know how to simulate a response.");
        }
    }
}
