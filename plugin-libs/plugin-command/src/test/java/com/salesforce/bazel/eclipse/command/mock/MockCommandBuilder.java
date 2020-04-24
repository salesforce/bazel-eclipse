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
package com.salesforce.bazel.eclipse.command.mock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.eclipse.command.Command;
import com.salesforce.bazel.eclipse.command.CommandBuilder;

/**
 * This is the main component of the mocking layer for the Bazel command line. This command builder
 * creates command objects that contain the textual output of simulated command invocations (e.g.
 * 'bazel build //xyz', bazel info workspace, bazel test //...).
 * <p>
 * Using this class, you can write tests that invoke Bazel without having the test hermeticity problems
 * of invoking the system Bazel executable.
 * <p>
 * Because a single invocation of a plugin API can trigger multiple Bazel commands, this command builder
 * sometimes needs to be configured to respond to multiple commands. This makes it a little more complicated.
 * Output is configured at the builder level, and then the proper output is written into each built command
 * object. The output is then returned with the command is run().
 * <p>
 * Some commands are handled with predetermined logic, and you don't need to worry about them. For example,
 * 'bazel info workspace' is handled for you without the need for configuring output.
 * Review the build() method to see the preconfigured commands.
 */
public class MockCommandBuilder extends CommandBuilder {
    // file paths
    private File bazelWorkspaceRoot;
    private File bazelOutputBase;
    private File bazelExecutionRoot;
    private File bazelBin;
    
    // .bazelrc options
    private Map<String, String> commandOptions;

    /**
     * This is an ordered list of output/error lines that will be returned from the Mock commands.
     * Tests need to populate one or more (depending on how many Bazel commands are run) that will simulate
     * the output/errors that the Bazel command(s) will produce.
     * <p>
     * For example, if your test will trigger 3 'bazel build xyz' commands to be run, you need to provide a 3 item array here.
     */
    public List<MockCommandSimulatedOutput> simulatedOutputLines = new ArrayList<>();

    /**
     * If an aspect build command is run, we need to know the list of aspect file paths for the workspace to be
     * able to create the specific output.
     */
    
    public MockCommandBuilder(CommandConsoleFactory consoleFactory, File bazelWorkspaceRoot, File bazelOutputBase, 
            File bazelExecutionRoot, File bazelBin, Map<String, String> commandOptions) {
        super(consoleFactory);
        this.bazelWorkspaceRoot = bazelWorkspaceRoot;
        this.bazelOutputBase = bazelOutputBase;
        this.bazelExecutionRoot = bazelExecutionRoot;
        this.bazelBin = bazelBin;
        this.commandOptions = commandOptions;
    }
    
    public MockCommandBuilder mockReturnOutputLines(List<String> outputLines) {
        return this;
    }

    public MockCommandBuilder mockReturnErrorLines(List<String> errorLines) {
        return this;
    }
    
    // STANDARD OUTPUT LINES
    // Use these methods as helpers to create standard output patterns
    
    /**
     * When the aspect build is run, the output lists the paths to all of the aspect files written
     * to disk. To simulate the aspect command output, you need to provide the list of aspect file paths
     * that are in the workspace. 
     * <p>
     * We need to use a Set of paths because the same aspect (ex. slf4j-api) will be used by multiple
     * mock bazel packages, so we need to make sure we only list each once
     * 
     * @param aspectFilePaths the set of file absolute paths to every aspect generated file
     */
    public void addAspectJsonFileResponses(Map<String, Set<String>> aspectFileSets) {
        // build command looks like: bazel build --override_repository=local_eclipse_aspect=/tmp/bef/bazelws/bazel-workspace/tools/aspect ...
        MockCommandSimulatedOutputMatcher aspectCommandMatcher1 = new MockCommandSimulatedOutputMatcher(1, "build");
        MockCommandSimulatedOutputMatcher aspectCommandMatcher2 = new MockCommandSimulatedOutputMatcher(2, ".*local_eclipse_aspect.*");
        
        for (String packagePath : aspectFileSets.keySet()) {
            // the last arg is the package path with the wildcard target (//projects/libs/javalib0:*)
            String wildcardTarget = "//"+packagePath+":.*"; // TODO this is returning the same set of aspects for each target in a package
            MockCommandSimulatedOutputMatcher aspectCommandMatcher3 = new MockCommandSimulatedOutputMatcher(7, wildcardTarget);

            List<MockCommandSimulatedOutputMatcher> matchers = new ArrayList<>();
            Collections.addAll(matchers, aspectCommandMatcher1, aspectCommandMatcher2, aspectCommandMatcher3);
    
            // stdout is used to print useless diagnostics, and stderr is a line per path to an aspect json file
            String[] outputLines = new String[] { "INFO: Analyzed 19 targets (0 packages loaded, 1 target configured).", "INFO: Found 19 targets...",
                    "INFO: Elapsed time: 0.146s, Critical Path: 0.00s", "INFO: Build completed successfully, 1 total action" };
            
            List<String> aspectFilePathsList = new ArrayList<>();
            aspectFilePathsList.addAll(aspectFileSets.get(packagePath));
            String nameForLog = "Aspect file set for target: "+wildcardTarget;
            MockCommandSimulatedOutput aspectOutput = new MockCommandSimulatedOutput(nameForLog, Arrays.asList(outputLines), aspectFilePathsList, matchers);
            simulatedOutputLines.add(aspectOutput);
        }
    }
    
    // CUSTOM OUTPUT LINES
    // If your use case invokes a command that will have specific output, use these methods
    
    /**
     * Simplest way to simulate a single custom command. When you use this method, the command builder
     * will use this output as the output for the next run command that doesn't match a preconfigured rule.
     * 
     * @param nameForLog when debugging a test, it is helpful to have a readable name for each configured output
     * @param outputLines the list of output lines, or null if none
     * @param errorLines the list of error lines, or null if none
     */
    public void addSimulatedOutput(String nameForLog, List<String> outputLines, List<String> errorLines) {
        MockCommandSimulatedOutput out = new MockCommandSimulatedOutput(nameForLog, outputLines, errorLines);
        simulatedOutputLines.add(out);
    }
    
    /**
     * Precise way to simulate a single custom command. When you use this method, the command builder
     * will use this output as the output for the next run command that matches the 'matchers' list.
     * 
     * @param nameForLog when debugging a test, it is helpful to have a readable name for each configured output
     * @param outputLines the list of output lines, or null if none
     * @param errorLines the list of error lines, or null if none
     * @param matchers one or more matchers that will specifically target a command
     */
    public void addSimulatedOutput(String nameForLog, List<String> outputLines, List<String> errorLines, List<MockCommandSimulatedOutputMatcher> matchers) {
        MockCommandSimulatedOutput out = new MockCommandSimulatedOutput(nameForLog, outputLines, errorLines);
        simulatedOutputLines.add(out);
    }
    
    
    // MOCK METHOD UNDER TEST
    
    @Override
    public Command build_impl() throws IOException {
        MockCommand mockCommand = new MockCommand();
        
        mockCommand.commandTokens = args;
        String commandPretty = "";
        for (String token : mockCommand.commandTokens) {
            commandPretty = commandPretty + token + " ";
        }
        
        // check if this is from a catalog of standard commands with stock responses
        boolean handled = false;
        if (mockCommand.commandTokens.get(0).endsWith("/bazel")) {
            if ("info".equals(mockCommand.commandTokens.get(1))) {
                // command is of the form 'bazel info' with an optional third param
            
                if (mockCommand.commandTokens.size() < 3) {
                    // this is just the generic 'bazel info', we probably should not be issuing this command from the plugins as there are better ways
                    throw new IllegalArgumentException("The plugin issued the command 'bazel info' without a third arg. Please consider using a more specific 'bazel info xyz' command instead.");
                } else if ("workspace".equals(mockCommand.commandTokens.get(2))) {
                    addSimulatedOutputToCommandStdOut(mockCommand, "INFO: Invocation ID: a6809b5e-3fb4-462e-8fcc-2c18575122e7", bazelWorkspaceRoot.getAbsolutePath());
                    handled = true;
                } else if ("execution_root".equals(mockCommand.commandTokens.get(2))) {
                    addSimulatedOutputToCommandStdOut(mockCommand, bazelExecutionRoot.getAbsolutePath());
                    handled = true;
                } else if ("output_base".equals(mockCommand.commandTokens.get(2))) {
                    addSimulatedOutputToCommandStdOut(mockCommand, bazelOutputBase.getAbsolutePath());
                    handled = true;
                } else if ("bazel-bin".equals(mockCommand.commandTokens.get(2))) {
                    addSimulatedOutputToCommandStdOut(mockCommand, bazelBin.getAbsolutePath());
                    handled = true;
                } else {
                    throw new IllegalArgumentException("MockCommandBuilder does not know how to mock 'bazel info "+mockCommand.commandTokens.get(2)+"'. Please add code to handle this case.");
                }
            } else if ("clean".equals(mockCommand.commandTokens.get(1))) {
                // "bazel clean"
                addSimulatedOutputToCommandStdOut(mockCommand, "INFO: Starting clean.");
                handled = true;
            } else if ("version".equals(mockCommand.commandTokens.get(1))) {
                // "bazel version"
                addSimulatedOutputToCommandStdOut(mockCommand, "Build label: 1.0.0", "Build time: Thu Oct 10 10:19:27 2019 (1570702767)",
                    "Build timestamp: 1570702767", "Build timestamp as int: 1570702767");
                handled = true;
            } else if ("build".equals(mockCommand.commandTokens.get(1))) {
                if (mockCommand.commandTokens.size() < 3) {
                    // this is just 'bazel build' without a target, which is not valid, blow up here as there is something wrong in the calling code
                    throw new IllegalArgumentException("The plugin issued the command 'bazel build' without a third arg. This is not a valid bazel command.");
                }
            } else if ("test".equals(mockCommand.commandTokens.get(1))) {
                if (mockCommand.commandTokens.size() < 3) {
                    // this is just 'bazel test' without a target, which is not valid, blow up here as there is something wrong in the calling code
                    throw new IllegalArgumentException("The plugin issued the command 'bazel test' without a third arg. This is not a valid bazel command.");
                }
                if (mockCommand.commandTokens.size() == 3 && "--announce_rc".equals(mockCommand.commandTokens.get(2))) {
                    if ("true".equals(commandOptions.get("explicit_java_test_deps"))) {
                        addSimulatedOutputToCommandStdErr(mockCommand, "   'test' options: --explicit_java_test_deps=true");
                    } else {
                        addSimulatedOutputToCommandStdErr(mockCommand, "   'test' options: --explicit_java_test_deps=false");
                    }
                    handled = true;
                }
            } else if ("query".equals(mockCommand.commandTokens.get(1))) {
                if (mockCommand.commandTokens.size() < 3) {
                    // this is just 'bazel build' without a target, which is not valid, blow up here as there is something wrong in the calling code
                    throw new IllegalArgumentException("The plugin issued the command 'bazel build' without a third arg. This is not a valid bazel command.");
                }
                // TODO mockcommand
                if (mockCommand.commandTokens.get(2).contains("javalib0")) {
                    addSimulatedOutputToCommandStdOut(mockCommand, "java_test rule //projects/libs/javalib0:javalib0Test");
                    addSimulatedOutputToCommandStdOut(mockCommand, "java_library rule //projects/libs/javalib0:javalib0");
                } else if (mockCommand.commandTokens.get(2).contains("javalib1")) {
                    addSimulatedOutputToCommandStdOut(mockCommand, "java_test rule //projects/libs/javalib1:javalib1Test");
                    addSimulatedOutputToCommandStdOut(mockCommand, "java_library rule //projects/libs/javalib1:javalib1");
                }

                handled = true;
            }
        } 
        
        // if it wasn't a standard command, get ready for it
        if (!handled) {
            for (MockCommandSimulatedOutput candidateOutput: this.simulatedOutputLines) {
                if (candidateOutput.doesMatch(mockCommand.commandTokens)) {
                    // the output is targeted to this command
                    mockCommand.outputLines = candidateOutput.outputLines;
                    mockCommand.errorLines = candidateOutput.errorLines;
                    handled = true;
                    break;
                }
            }
        }
        
        if (!handled) {
            throw new IllegalStateException("The MockCommandBuilder does not have enough output to provide to simulate the necessary Bazel commands. There are "+
                    simulatedOutputLines.size()+" outputs configured. Command:\n" + commandPretty);
        }

        return mockCommand;
    }

    private void addSimulatedOutputToCommandStdOut(MockCommand mockCommand, String... someStrings) {
        mockCommand.outputLines = new ArrayList<>();
        for (String someString : someStrings) {
            mockCommand.outputLines.add(someString);
        }
        mockCommand.errorLines = new ArrayList<>();
    }

    private void addSimulatedOutputToCommandStdErr(MockCommand mockCommand, String... someStrings) {
        mockCommand.errorLines = new ArrayList<>();
        for (String someString : someStrings) {
            mockCommand.errorLines.add(someString);
        }
        mockCommand.outputLines = new ArrayList<>();
    }

    /**
     * Filter that enables Bazel command output to be associated with a particular command.
     * By providing a list of these, you can make sure that the output lines are only returned to
     * the caller if all matchers return true.  
     */
    public static class MockCommandSimulatedOutputMatcher {
        public int matchArgIndex;
        public String matchArgRegex;
        
        public MockCommandSimulatedOutputMatcher(int index, String regex) {
            this.matchArgIndex = index;
            this.matchArgRegex = regex;
        }
    }
    
    /**
     * Provides the output (output and error lines) from a simulated run of a Bazel command.
     */
    public static class MockCommandSimulatedOutput {
        public String nameForLog;
        public List<MockCommandSimulatedOutputMatcher> matchers;
        public int matchesRemaining = Integer.MAX_VALUE;
        public List<String> outputLines;
        public List<String> errorLines;

        public MockCommandSimulatedOutput(String nameForLog) {
            this.nameForLog = nameForLog;
        }

        public MockCommandSimulatedOutput(String nameForLog, List<String> outputLines, List<String> errorLines) {
            this.nameForLog = nameForLog;
            this.outputLines = outputLines;
            this.errorLines = errorLines;
        }

        public MockCommandSimulatedOutput(String nameForLog, List<String> outputLines, List<String> errorLines, List<MockCommandSimulatedOutputMatcher> matchers) {
            this.nameForLog = nameForLog;
            this.outputLines = outputLines;
            this.errorLines = errorLines;
            this.matchers = matchers;
        }

        /**
         * Determines if this output is to be used for the command.
         */
        public boolean doesMatch(List<String> commandArgs) {
            if (matchesRemaining < 1) {
                // this matcher was configured to be 'consumed' and not reused
                return false;
            }
            if (matchers == null) {
                // test probably is only running one command, so just return true
                matchesRemaining--;
                return true;
            }
            // the matchers must all be true (AND) for the command to match
            for (MockCommandSimulatedOutputMatcher matcher : matchers) {
                if (commandArgs.size() <= matcher.matchArgIndex) {
                    // not enough args to match against, so this can't be our command
                    return false;
                }
                String pattern = matcher.matchArgRegex;
                String arg = commandArgs.get(matcher.matchArgIndex);
                if (!arg.matches(pattern)) {
                    return false;
                }
            }
            matchesRemaining--;
            return true;
        }
        
    }
    
}
