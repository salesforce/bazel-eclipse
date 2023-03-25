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

package com.salesforce.bazel.sdk.command.shell;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.command.BazelProcessBuilder;
import com.salesforce.bazel.sdk.command.Command;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsole;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * A utility class to spawn a command in the shell and parse its output. It allows to filter the output, redirecting
 * part of it to the console and getting the rest in a list of string.
 * <p>
 * This class can only be initialized using a builder created with the {@link #builder()} method.
 */
public final class ShellCommand implements Command {
    private static class CopyStreamRunnable implements Runnable {
        private final InputStream inputStream;
        private final OutputStream outputStream;

        CopyStreamRunnable(InputStream inputStream, OutputStream outputStream) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {
            var buffer = new byte[4096];
            int read;
            try {
                while ((read = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, read);
                }
            } catch (Exception ex) {
                LOG.error("Error writing command stream to the channel.", ex);
                // we simply terminate the thread on exceptions
            }
        }
    }

    /**
     * LogHelper for the ShellCommand. It is not final, and public, so that your application can replace the LogHelper
     * for just this class. Logging the execution of the shell commands is likely something you will want to tailor for
     * your exact use case.
     */
    private static Logger LOG = LoggerFactory.getLogger(ShellCommand.class);

    /**
     * Returns a {@link CommandBuilder} object to use to create a {@link ShellCommand} object.
     */
    public static CommandBuilder builder(CommandConsoleFactory consoleFactory, ShellEnvironment environment) {
        return new ShellCommandBuilder(consoleFactory, environment);
    }

    private static void closeQuietly(OutputStream os) {
        try {
            os.close();
        } catch (Exception ignore) {}
    }

    // Launch a thread to copy all data from inputStream to outputStream
    private static Thread copyStream(InputStream inputStream, OutputStream outputStream) {
        if (outputStream != null) {
            var t = new Thread(new CopyStreamRunnable(inputStream, outputStream), "CopyStream");
            t.start();
            return t;
        }
        return null;
    }

    /* visible for testing */
    static String toQuotedStringForShell(List<String> args) {
        var result = new StringBuilder();
        for (String arg : args) {
            if (result.length() > 0) {
                result.append(' ');
            }
            var quoteArg = (arg.indexOf(' ') > -1) && !arg.startsWith("\\\"");
            if (quoteArg) {
                result.append("\"");
            }
            result.append(arg.replace("\"", "\\\""));
            if (quoteArg) {
                result.append("\"");
            }
        }
        return result.toString();
    }

    private final File directory;

    private final List<String> args;

    private final SelectOutputStream stdout;

    private final SelectOutputStream stderr;

    private final WorkProgressMonitor progressMonitor;

    private final ShellEnvironment shellEnvironment;

    // TODO ShellCommand timeouts are not usable; if a command times out subsequent commands hang, etc.
    // https://github.com/salesforce/bazel-eclipse/issues/191
    private long timeoutMS = 0;

    private boolean executed = false;

    ShellCommand(CommandConsole console, File directory, List<String> args, Function<String, String> stdoutSelector,
            Function<String, String> stderrSelector, OutputStream stdout, OutputStream stderr,
            WorkProgressMonitor progressMonitor, long timeoutMS, ShellEnvironment shellEnvironment) {
        this.directory = directory;
        this.args = args;
        this.shellEnvironment = requireNonNull(shellEnvironment);
        if (console != null) {
            if (stdout == null) {
                stdout = console.createOutputStream();
            }
            if (stderr == null) {
                stderr = console.createErrorStream();
            }
        }
        this.stderr = new SelectOutputStream(stderr, stderrSelector);
        this.stdout = new SelectOutputStream(stdout, stdoutSelector);
        this.progressMonitor = progressMonitor;
        this.timeoutMS = timeoutMS;

    }

    /**
     * Returns a ProcessBuilder configured to run this Command instance.
     */
    @Override
    public BazelProcessBuilder getProcessBuilder() {
        // TODO make env variables sent to ShellCommand configurable
        // https://github.com/salesforce/bazel-eclipse/issues/190
        Map<String, String> bazelEnvironmentVariables = new HashMap<>();
        bazelEnvironmentVariables.put("PULLER_TIMEOUT", "3000"); // increases default timeout from 600 to 3000 seconds for rules_docker downloads

        BazelProcessBuilder builder;
        if (shellEnvironment.launchWithBashEnvironment()) {
            // FIXME: should only do this on OSX as '-l' login shell; linux should use non-login shell
            // FIXME: read login shell on OSX with 'dscl . -read /Users/$USER UserShell'
            // FIXME: parse /etc/passwd in Linux (split by ':', 7 fields, login shell is last)
            List<String> bashArgs = List.of("bash", "-l", "-c", toQuotedStringForShell(args));
            builder = new BazelProcessBuilder(bashArgs, bazelEnvironmentVariables);
        } else {
            builder = new BazelProcessBuilder(args, bazelEnvironmentVariables);
        }
        builder.directory(directory);
        return builder;
    }

    /**
     * Returns the list of lines selected from the standard error stream. Lines printed to the standard error stream by
     * the executed command can be filtered to be added to that list.
     *
     * @see {@link CommandBuilder#setStderrLineSelector(Function)}
     */
    @Override
    public List<String> getSelectedErrorLines() {
        return stderr.getLines();
    }

    /**
     * Returns the list of lines selected from the standard output stream. Lines printed to the standard output stream
     * by the executed command can be filtered to be added to that list.
     *
     * @see {@link CommandBuilder#setStdoutLineSelector(Function)}
     */
    @Override
    public List<String> getSelectedOutputLines() {
        return stdout.getLines();
    }

    /**
     * Executes the command represented by this instance, and return the exit code of the command. This method should
     * not be called twice on the same object.
     *
     * @throws CoreException
     */
    @Override
    public int run() throws IOException, InterruptedException {
        if (executed) {
            throw new IllegalStateException("This command has already been run.");
        }
        executed = true;
        var builder = getProcessBuilder();
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        Process process = null;
        try {
            process = builder.start();
        } catch (Exception anyE) {
            // this can blow up on Windows with error 5 (Access Denied) if the msys64 bash is not on disk
            throw anyE;
        }

        // TODO implement the progress monitor for command line invocations
        if (progressMonitor != null) {
            progressMonitor.worked(1);
        }
        var command = "";
        for (String arg : args) {
            command = command + arg + " ";
        }
        LOG.debug("Executing command (timeout = {}): {}", timeoutMS, command);
        var startTimeMS = System.currentTimeMillis();
        var success = false;

        try {
            var err = copyStream(process.getErrorStream(), stderr);
            var out = copyStream(process.getInputStream(), stdout);
            var exitCode = process.waitFor();
            if (err != null) {
                err.join(timeoutMS);
            }
            if (out != null) {
                out.join(timeoutMS);
            }
            success = exitCode == 0;
            return exitCode;
        } catch (InterruptedException interrupted) {
            throw interrupted;
        } finally {
            closeQuietly(stderr);
            closeQuietly(stdout);
            if (args.size() > 1) {
                // arg 1 typically has the more interesting command token
                SimplePerfRecorder.addTime("commmand_" + args.get(1), startTimeMS);
            } else {
                SimplePerfRecorder.addTime("commmand_" + args.get(0), startTimeMS);
            }

            // report results to console
            var elapsedTimeMS = System.currentTimeMillis() - startTimeMS;
            LOG.debug("Finished command ({} millis) (success={}): {}", elapsedTimeMS, success, command);
        }
    }
}
