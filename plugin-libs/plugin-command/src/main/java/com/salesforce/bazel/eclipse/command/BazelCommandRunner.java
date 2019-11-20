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
 * 
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.salesforce.bazel.eclipse.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;

/**
 * Utility class that understands how to formulate Bazel command line commands.
 * <p>
 * TODO this class needs a refactor, why all the slightly different method signatures?
 */
class BazelCommandRunner {
    private final BazelCommandFacade bazelCommandFacade;
    private final CommandBuilder commandBuilder;

    static enum ConsoleType {
        NO_CONSOLE, SYSTEM, WORKSPACE
    }

    BazelCommandRunner(BazelCommandFacade bazelCommandFacade, CommandBuilder commandBuilder) {
        this.bazelCommandFacade = bazelCommandFacade;
        this.commandBuilder = commandBuilder;
    }

    synchronized List<String> runBazelCommand(File workingDirectory, WorkProgressMonitor progressMonitor,
            String... args) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        List<String> argList = ImmutableList.<String> builder().add(args).build();
        return this.runBazelAndGetOuputLines(ConsoleType.WORKSPACE, workingDirectory, progressMonitor, argList, (t) -> t);
    }

    synchronized List<String> runBazelAndGetErrorLines(File directory, WorkProgressMonitor progressMonitor,
            List<String> args, List<String> bazelTargets, Function<String, String> selector)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        CommandBuilder builder = getConfiguredCommandBuilder(ConsoleType.WORKSPACE, directory, progressMonitor, args);
        Command command = builder.setStderrLineSelector(selector).build();
        command.run();
        return command.getSelectedErrorLines();
    }

    synchronized List<String> runBazelAndGetOuputLines(ConsoleType type, File workingDirectory,
            WorkProgressMonitor progressMonitor, List<String> args, Function<String, String> selector)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        
        Command command = commandBuilder.setConsoleName(getConsoleName(type, workingDirectory))
                .setDirectory(workingDirectory).addArguments(bazelCommandFacade.getBazelExecutablePath())
                .addArguments(args).setStdoutLineSelector(selector).setProgressMonitor(progressMonitor).build();
        if (command.run() == 0) {
            return command.getSelectedOutputLines();
        }
        return ImmutableList.of();
    }

    synchronized List<String> runBazelAndGetErrorLines(ConsoleType consoleType, File directory,
            WorkProgressMonitor progressMonitor, List<String> args, Function<String, String> selector)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        CommandBuilder builder = getConfiguredCommandBuilder(consoleType, directory, progressMonitor, args);
        Command command = builder.setStderrLineSelector(selector).build();
        if (command.run() == 0) {
            return command.getSelectedErrorLines();
        }
        return ImmutableList.of();
    }

    /**
     * Builds, but does not run, a Bazel command line
     *
     * @return the Command instance encapsulating the command to run on the command line
     */
    Command buildBazelCommand(File directory, WorkProgressMonitor progressMonitor, List<String> args)
            throws IOException, BazelCommandLineToolConfigurationException {
        CommandBuilder builder = getConfiguredCommandBuilder(ConsoleType.WORKSPACE, directory, progressMonitor, args);
        return builder.build();
    }

    /**
     * Runs Bazel on the command line
     *
     * @return the process exit code
     */
    int runBazel(File directory, WorkProgressMonitor progressMonitor, List<String> args)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        CommandBuilder builder = getConfiguredCommandBuilder(ConsoleType.WORKSPACE, directory, progressMonitor, args);
        Command cmd = builder.build();
        return cmd.run();
    }

    /**
     * Utility method to strip INFO log lines from the output lines returned when running the command
     */
    public static List<String> stripInfoLines(List<String> outputLines) {
        // TODO just build this into one of the options for running the command
        List<String> outputLinesStripped = new ArrayList<>();
        for (String line : outputLines) {
            if (line.startsWith("INFO:")) {
                continue;
            }
            outputLinesStripped.add(line);
        }
        return outputLinesStripped;
    }
    
    
    
    private CommandBuilder getConfiguredCommandBuilder(ConsoleType type, File directory,
            WorkProgressMonitor progressMonitor, List<String> args) throws BazelCommandLineToolConfigurationException {
        
        String consoleName = getConsoleName(type, directory);
        String executablePath = bazelCommandFacade.getBazelExecutablePath();
        
        return commandBuilder
                .setConsoleName(consoleName)
                .setDirectory(directory)
                .addArguments(executablePath)
                .addArguments(args)
                .setProgressMonitor(progressMonitor);
    }

    private String getConsoleName(ConsoleType type, File directory) {
        switch (type) {
        case SYSTEM:
            return "Bazel [system]";
        case WORKSPACE:
            return "Bazel [" + directory.toString() + "]";
        case NO_CONSOLE:
        default:
            return null;
        }
    }

}
