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

package com.salesforce.bazel.eclipse.command.shell;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.CommandConsole;
import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.eclipse.abstractions.OutputStreamObserver;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.BazelProcessBuilder;
import com.salesforce.bazel.eclipse.command.Command;
import com.salesforce.bazel.eclipse.command.CommandBuilder;

/**
 * A utility class to spawn a command in the shell and parse its output. It allows to filter the output, 
 * redirecting part of it to the console and getting the rest in a list of string.
 * <p>
 * This class can only be initialized using a builder created with the {@link #builder()} method.
 */
public final class ShellCommand implements Command {

    private final File directory;
    private final ImmutableList<String> args;
    private final SelectOutputStream stdout;
    private final SelectOutputStream stderr;
    private final WorkProgressMonitor progressMonitor;
    private final long timeoutMS;

    private boolean executed = false;

    ShellCommand(CommandConsole console, File directory, ImmutableList<String> args,
            Function<String, String> stdoutSelector, Function<String, String> stderrSelector, OutputStream stdout,
            OutputStream stderr, OutputStreamObserver stdoutObserver, OutputStreamObserver stderrObserver, WorkProgressMonitor progressMonitor, long timeoutMS) {
        this.directory = directory;
        this.args = args;
        if (console != null) {
            if (stdout == null) {
                stdout = console.createOutputStream();
            }
            if (stderr == null) {
                stderr = console.createErrorStream();
            }
        }
        this.stderr = new SelectOutputStream(stderr, stderrSelector, stderrObserver);
        this.stdout = new SelectOutputStream(stdout, stdoutSelector, stdoutObserver);
        this.progressMonitor = progressMonitor;
        this.timeoutMS = timeoutMS;
    }

    /**
     * Returns a ProcessBuilder configured to run this Command instance.
     */
    public BazelProcessBuilder getProcessBuilder() {
        BazelProcessBuilder builder = new BazelProcessBuilder(args);
        builder.directory(directory);
        return builder;
    }

    /**
     * Executes the command represented by this instance, and return the exit code of the command. This method should
     * not be called twice on the same object.
     *
     * @throws CoreException
     */
    @Override
    public int run() throws IOException, InterruptedException {
        Preconditions.checkState(!executed);
        executed = true;
        BazelProcessBuilder builder = getProcessBuilder();
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        Process process = builder.start();
        // TODO implement the progress monitor for command line invocations
        if (this.progressMonitor != null) {
            this.progressMonitor.worked(1);
        }
        String command = "";
        for (String arg : args) {
            command = command + arg + " ";
        }
        System.out.println("Executing command: "+command);

        try {
            Thread err = copyStream(process.getErrorStream(), stderr);
            Thread out = copyStream(process.getInputStream(), stdout);
            int exitCode = process.waitFor();
            if (err != null) {
                err.join(timeoutMS);
            }
            if (out != null) {
                out.join(timeoutMS);
            }
            return exitCode;
        } catch (InterruptedException interrupted) {
            throw interrupted;
        }
        finally {
            closeQuietly(stderr);
            closeQuietly(stdout);
        }
    }

    private static void closeQuietly(OutputStream os) {
        try {
            os.close();
        } catch (Exception ignore) {}
    }

    private static class CopyStreamRunnable implements Runnable {
        private InputStream inputStream;
        private OutputStream outputStream;

        CopyStreamRunnable(InputStream inputStream, OutputStream outputStream) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            int read;
            try {
                while ((read = inputStream.read(buffer)) > 0) {
                    synchronized (outputStream) {
                        outputStream.write(buffer, 0, read);
                    }
                }
            } catch (IOException ex) {
                // we simply terminate the thread on exceptions
            }
        }
    }

    // Launch a thread to copy all data from inputStream to outputStream
    private static Thread copyStream(InputStream inputStream, OutputStream outputStream) {
        if (outputStream != null) {
            Thread t = new Thread(new CopyStreamRunnable(inputStream, outputStream), "CopyStream");
            t.start();
            return t;
        }
        return null;
    }

    /**
     * Returns the list of lines selected from the standard error stream. Lines printed to the standard error stream by
     * the executed command can be filtered to be added to that list.
     *
     * @see {@link CommandBuilder#setStderrLineSelector(Function)}
     */
    @Override
    public ImmutableList<String> getSelectedErrorLines() {
        return stderr.getLines();
    }

    /**
     * Returns the list of lines selected from the standard output stream. Lines printed to the standard output stream
     * by the executed command can be filtered to be added to that list.
     *
     * @see {@link CommandBuilder#setStdoutLineSelector(Function)}
     */
    @Override
    public ImmutableList<String> getSelectedOutputLines() {
        return stdout.getLines();
    }

    /**
     * Returns a {@link CommandBuilder} object to use to create a {@link ShellCommand} object.
     */
    public static CommandBuilder builder(CommandConsoleFactory consoleFactory) {
        return new ShellCommandBuilder(consoleFactory);
    }
}
