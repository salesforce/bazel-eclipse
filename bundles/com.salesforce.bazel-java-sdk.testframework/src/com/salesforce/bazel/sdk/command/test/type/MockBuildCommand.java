package com.salesforce.bazel.sdk.command.test.type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.salesforce.bazel.sdk.command.internal.BazelWorkspaceAspectProcessor;
import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.command.test.MockCommandSimulatedOutput;
import com.salesforce.bazel.sdk.command.test.MockCommandSimulatedOutputMatcher;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Simulates a "bazel build //a/b/c" command
 */
public class MockBuildCommand extends MockCommand {

    public MockBuildCommand(List<String> commandTokens, TestOptions testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens, testOptions, testWorkspaceFactory);

        if (commandTokens.size() < 3) {
            // this is just 'bazel build' without a target, which is not valid, blow up here as there is something wrong in the calling code
            throw new IllegalArgumentException(
                    "The plugin issued the command 'bazel build' without a third arg. This is not a valid bazel command.");
        }

        // check that the target (e.g. projects/libs/javalib0) is valid relative to our test workspace
        String target = findBazelTargetInArgs();
        if (!isValidBazelTarget(target)) {
            // by default, isValidBazelTarget() will throw an exception if the package is missing, but the test may configure it to return false instead
            errorLines = Arrays.asList("ERROR: no such package '" + target
                + "': BUILD file not found in any of the following directories. Add a BUILD file to a directory to mark it as a package.", "- /fake/path/" + target); // // $SLASH_OK: bazel path
            return;
        }

        // We have two major types of builds:
        //  - generating the aspect json files
        //  - code builds
        // We will fork based on build type...
        if (commandTokens.get(2).contains("bazeljavasdk_aspect")) {
            createAspectBuildCommand();
        } else {
            createCodeBuildCommand();
        }
    }

    /**
     * When the aspect build is run, the output lists the paths to all of the aspect files written to disk. To simulate
     * the aspect command output, the MockBuildCommand needs to know the list of aspect file paths that are in the
     * workspace.
     * <p>
     * We need to use a Set of paths because the same aspect (ex. slf4j-api) will be used by multiple mock bazel
     * packages, so we need to make sure we only list each once
     */
    void createAspectBuildCommand() {
        List<MockCommandSimulatedOutput> simulatedOutputLines = new ArrayList<>();
        // TODO this aspect code here is indirect, it should just populate out/err lines directly
        // TODO clean up "Problem adding jar to project" errors seen when running tests from Eclipse, seems to be during aspect phase
        // stderr is a line per path to an aspect json file

        // build command looks like: bazel build --override_repository=bazeljavasdk_aspect=/tmp/bef/bazelws/bazel-workspace/tools/aspect ...
        MockCommandSimulatedOutputMatcher aspectCommandMatcher1 = new MockCommandSimulatedOutputMatcher(1, "build");
        MockCommandSimulatedOutputMatcher aspectCommandMatcher2 =
                new MockCommandSimulatedOutputMatcher(BazelWorkspaceAspectProcessor.ASPECTCMD_EXTERNALREPO_ARGINDEX,
                        ".*bazeljavasdk_aspect.*");

        for (String packagePath : testWorkspaceFactory.workspaceDescriptor.aspectFileSets.keySet()) {
            // the last arg is the package path with the wildcard target (//projects/libs/javalib0:*)
            // TODO this is returning the same set of aspects for each target in a package
            String wildcardTarget =
                    BazelLabel.BAZEL_ROOT_SLASHES + packagePath + BazelLabel.BAZEL_COLON + ".*";
            MockCommandSimulatedOutputMatcher aspectCommandMatcher3 =
                    new MockCommandSimulatedOutputMatcher(BazelWorkspaceAspectProcessor.ASPECTCMD_TARGETLABEL_ARGINDEX,
                        wildcardTarget);

            List<MockCommandSimulatedOutputMatcher> matchers = new ArrayList<>();
            Collections.addAll(matchers, aspectCommandMatcher1, aspectCommandMatcher2, aspectCommandMatcher3);

            List<String> aspectFilePathsList = new ArrayList<>(testWorkspaceFactory.workspaceDescriptor.aspectFileSets.get(packagePath));
            String nameForLog = "Aspect file set for target: " + wildcardTarget;
            MockCommandSimulatedOutput aspectOutput =
                    new MockCommandSimulatedOutput(nameForLog, outputLines, aspectFilePathsList, matchers);
            simulatedOutputLines.add(aspectOutput);
        }
        for (MockCommandSimulatedOutput candidateOutput : simulatedOutputLines) {
            if (candidateOutput.doesMatch(commandTokens)) {
                // the output is targeted to this command
                outputLines = candidateOutput.outputLines;
                errorLines = candidateOutput.errorLines;
                break;
            }
        }
    }

    void createCodeBuildCommand() {
        List<MockCommandSimulatedOutput> simulatedOutputLines = new ArrayList<>();

        // stdout is used to print diagnostics
        // assume the build will succeed and pre-set the stdout message (something further down may set this differently though)
        // note the time, target count, and action count are all static; if you want to write tests that inspect those values you have a lot of work to do here
        outputLines =
                Arrays.asList("INFO: Analyzed 19 targets (0 packages loaded, 1 target configured).", "INFO: Found 19 targets...", "INFO: Elapsed time: 0.146s, Critical Path: 0.00s", "INFO: Build completed successfully, 1 total action");

        // TODO derive build output from test workspace structure
        // TODO allow testOptions to determine that a package build should fail
        // TODO fail if passed package is not in test workspace
        for (MockCommandSimulatedOutput candidateOutput : simulatedOutputLines) {
            if (candidateOutput.doesMatch(commandTokens)) {
                // the output is targeted to this command
                outputLines = candidateOutput.outputLines;
                errorLines = candidateOutput.errorLines;
                break;
            }
        }
    }
}
