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

package com.salesforce.bazel.eclipse.command.internal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.eclipse.command.Command;
import com.salesforce.bazel.eclipse.command.CommandBuilder;

/**
 * Utility class that understands how to run Command objects and collect output from them.
 */
public class BazelCommandExecutor {
    private final File bazelExecutable;
    private final CommandBuilder commandBuilder;

    public BazelCommandExecutor(File bazelExecutable, CommandBuilder commandBuilder) {
        this.bazelExecutable = bazelExecutable;
        this.commandBuilder = commandBuilder;
    }

    // WHEN INTERESTING OUTPUT IS ON STDOUT...

    public synchronized List<String> runBazelAndGetOutputLines(File workingDirectory, WorkProgressMonitor progressMonitor,
            List<String> args, Function<String, String> selector) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        CommandBuilder builder = getConfiguredCommandBuilder(ConsoleType.WORKSPACE, workingDirectory, progressMonitor, args);
        Command command = builder.setStdoutLineSelector(selector).build();
        command.run();

        return command.getSelectedOutputLines();
    }


    public synchronized List<String> runBazelAndGetOuputLines(ConsoleType consoleType, File workingDirectory,
            WorkProgressMonitor progressMonitor, List<String> args, Function<String, String> selector)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        
        CommandBuilder builder = getConfiguredCommandBuilder(consoleType, workingDirectory, progressMonitor, args);
        Command command = builder.setStdoutLineSelector(selector).build();

        if (command.run() == 0) {
            return command.getSelectedOutputLines();
        }
        return ImmutableList.of();
    }

    // WHEN INTERESTING OUTPUT IS ON STDERR...
    
    public synchronized List<String> runBazelAndGetErrorLines(File directory, WorkProgressMonitor progressMonitor,
            List<String> args, Function<String, String> selector)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        
        CommandBuilder builder = getConfiguredCommandBuilder(ConsoleType.WORKSPACE, directory, progressMonitor, args);
        Command command = builder.setStderrLineSelector(selector).build();
        command.run();

        return command.getSelectedErrorLines();
    }

    public synchronized List<String> runBazelAndGetErrorLines(ConsoleType consoleType, File directory,
            WorkProgressMonitor progressMonitor, List<String> args, Function<String, String> selector)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
       
        CommandBuilder builder = getConfiguredCommandBuilder(consoleType, directory, progressMonitor, args);
        Command command = builder.setStderrLineSelector(selector).build();
        if (command.run() == 0) {
            return command.getSelectedErrorLines();
        }
        
        return ImmutableList.of();
    }
    
    
    // HELPERS

    /**
     * Utility method to strip INFO log lines from the output lines returned when running the command
     */
    public static List<String> stripInfoLines(List<String> outputLines) {
        List<String> outputLinesStripped = new ArrayList<>();
        for (String line : outputLines) {
            if (line.startsWith("INFO:")) {
                continue;
            }
            outputLinesStripped.add(line);
        }
        return outputLinesStripped;
    }
    
    
    // INTERNAL
    
    private CommandBuilder getConfiguredCommandBuilder(ConsoleType type, File directory,
            WorkProgressMonitor progressMonitor, List<String> args) throws BazelCommandLineToolConfigurationException {
        
        String consoleName = type.getConsoleName(directory);
        
        return commandBuilder
                .setConsoleName(consoleName)
                .setDirectory(directory)
                .addArguments(this.bazelExecutable.getAbsolutePath())
                .addArguments(args)
                .setProgressMonitor(progressMonitor);
    }


}
