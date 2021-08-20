/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.sdk.command.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.command.Command;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.command.test.type.MockBuildCommand;
import com.salesforce.bazel.sdk.command.test.type.MockCleanCommand;
import com.salesforce.bazel.sdk.command.test.type.MockCustomCommand;
import com.salesforce.bazel.sdk.command.test.type.MockInfoCommand;
import com.salesforce.bazel.sdk.command.test.type.MockLauncherCommand;
import com.salesforce.bazel.sdk.command.test.type.MockQueryCommand;
import com.salesforce.bazel.sdk.command.test.type.MockTestCommand;
import com.salesforce.bazel.sdk.command.test.type.MockVersionCommand;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * This is the main component of the mocking layer for the Bazel command line. This command builder creates command
 * objects that contain the textual output of simulated command invocations (e.g. 'bazel build //xyz', bazel info
 * workspace, bazel test //...).
 * <p>
 * Using this class, you can write tests that invoke Bazel without having the test hermeticity problems of invoking the
 * system Bazel executable.
 * <p>
 * Because a single invocation of a plugin API can trigger multiple Bazel commands, this command builder sometimes needs
 * to be configured to respond to multiple commands. This makes it a little more complicated. Output is configured at
 * the builder level, and then the proper output is written into each built command object. The output is then returned
 * with the command is run().
 * <p>
 * Some commands are handled with predetermined logic, and you don't need to worry about them. For example, 'bazel info
 * workspace' is handled for you without the need for configuring output. Review the build() method to see the
 * preconfigured commands.
 */
public class MockCommandBuilder extends CommandBuilder {
    // Workspace under test
    private final TestBazelWorkspaceFactory testWorkspaceFactory;

    // arbitrary option map provided by the test, can be interpreted by the Commands in a specific way
    // see the Mock*Command classes for details on what is available
    // for example, you may wish for a certain package to fail to build, that is an option in MockBuildCommand
    private final TestOptions testOptions;

    /**
     * This is an ordered list of output/error lines that will be returned from the Mock commands. Tests need to
     * populate one or more (depending on how many Bazel commands are run) that will simulate the output/errors that the
     * Bazel command(s) will produce.
     * <p>
     * For example, if your test will trigger 3 'bazel build xyz' commands to be run, you need to provide a 3 item array
     * here.
     */
    public List<MockCommandSimulatedOutput> simulatedOutputLines = new ArrayList<>();

    /**
     * If an aspect build command is run, we need to know the list of aspect file paths for the workspace to be able to
     * create the specific output.
     */

    public MockCommandBuilder(CommandConsoleFactory consoleFactory, TestBazelWorkspaceFactory testWorkspaceFactory,
            TestOptions testOptions) {
        super(consoleFactory);
        this.testWorkspaceFactory = testWorkspaceFactory;
        this.testOptions = testOptions;
    }

    public MockCommandBuilder mockReturnOutputLines(List<String> outputLines) {
        return this;
    }

    public MockCommandBuilder mockReturnErrorLines(List<String> errorLines) {
        return this;
    }

    // OUTPUT LINES FOR CUSTOM OR LAUNCHER COMMANDS
    // For use cases in which arbitrary non-standard commands are run, or for use cases in which a launcher
    // script is run ("bazel run //a/b/c") you will need to provide the output.

    /**
     * Simplest way to simulate a single custom command. When you use this method, the command builder will use this
     * output as the output for the next run command that doesn't match a preconfigured rule.
     *
     * @param nameForLog
     *            when debugging a test, it is helpful to have a readable name for each configured output
     * @param outputLines
     *            the list of output lines, or null if none
     * @param errorLines
     *            the list of error lines, or null if none
     */
    public void addSimulatedOutput(String nameForLog, List<String> outputLines, List<String> errorLines) {
        MockCommandSimulatedOutput out = new MockCommandSimulatedOutput(nameForLog, outputLines, errorLines);
        simulatedOutputLines.add(out);
    }

    /**
     * Precise way to simulate a single custom command. When you use this method, the command builder will use this
     * output as the output for the next run command that matches the 'matchers' list.
     *
     * @param nameForLog
     *            when debugging a test, it is helpful to have a readable name for each configured output
     * @param outputLines
     *            the list of output lines, or null if none
     * @param errorLines
     *            the list of error lines, or null if none
     * @param matchers
     *            one or more matchers that will specifically target a command
     */
    public void addSimulatedOutput(String nameForLog, List<String> outputLines, List<String> errorLines,
            List<MockCommandSimulatedOutputMatcher> matchers) {
        MockCommandSimulatedOutput out = new MockCommandSimulatedOutput(nameForLog, outputLines, errorLines, matchers);
        simulatedOutputLines.add(out);
    }

    // MOCK METHOD UNDER TEST

    @Override
    public Command build_impl() throws IOException {
        MockCommand mockCommand = null;

        // check if this is from a catalog of standard commands with stock responses
        if (args.get(0).endsWith(File.separatorChar + "bazel")) {
            if ("info".equals(args.get(1))) {
                // command is of the form 'bazel info' with an optional third param
                mockCommand = new MockInfoCommand(args, testOptions, testWorkspaceFactory);
            } else if ("clean".equals(args.get(1))) {
                // "bazel clean"
                mockCommand = new MockCleanCommand(args, testOptions, testWorkspaceFactory);
            } else if ("version".equals(args.get(1))) {
                // "bazel version"
                mockCommand = new MockVersionCommand(args, testOptions, testWorkspaceFactory);
            } else if ("build".equals(args.get(1))) {
                // bazel build xyz
                mockCommand = new MockBuildCommand(args, testOptions, testWorkspaceFactory);
            } else if ("test".equals(args.get(1))) {
                // bazel test xyz
                mockCommand = new MockTestCommand(args, testOptions, testWorkspaceFactory);
            } else if ("query".equals(args.get(1))) {
                mockCommand = new MockQueryCommand(args, testOptions, testWorkspaceFactory);
            }
        } else if (isLauncherInvocation(args.get(0))) {
            // launcher script (test must provide the desired output lines)
            mockCommand = new MockLauncherCommand(args, testOptions, testWorkspaceFactory, simulatedOutputLines);
        }

        // if it wasn't a standard command, setup a custom command responder (test must provide the desired output lines)
        if (mockCommand == null) {
            mockCommand = new MockCustomCommand(args, testOptions, testWorkspaceFactory, simulatedOutputLines);
        }

        return mockCommand;
    }

    private boolean isLauncherInvocation(String firstArg) {
        if (firstArg.startsWith("bazel-bin")) {
            return true;
        }
        if (firstArg.startsWith("/private")) {
            // Mac
            return true;
        }
        if (firstArg.startsWith("C:")) {
            // Windows
            return true;
        }
        return false;
    }

}
