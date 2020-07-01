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
package com.salesforce.bazel.sdk.command;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.salesforce.bazel.sdk.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.sdk.abstractions.OutputStreamObserver;
import com.salesforce.bazel.sdk.abstractions.WorkProgressMonitor;

/**
 * A base builder class to generate a Bazel Command object. This is a low level API
 * and typically there are convenience methods in BazelWorkspaceCommandRunner that
 * will create the commands for you.
 * <p>
 * Specific implementations include the ShellCommandBuilder (which creates actual shell invocations) 
 * and the MockCommandBuilder (for running functional tests that simulate shell invocations).
 * <p>
 * As currently implemented, this class is not thread-safe. Meaning a single builder is stateful and
 * can only build one command object at a time. Invoking build() clears the state and makes the builder
 * ready for the next command to build.
 */
public abstract class CommandBuilder {

    protected String consoleName = null;
    protected File directory;
    protected List<String> args;
    protected OutputStream stdout = null;
    protected OutputStream stderr = null;
    protected OutputStreamObserver stdoutObserver = null;
    protected OutputStreamObserver stderrObserver = null;
    protected Function<String, String> stdoutSelector;
    protected Function<String, String> stderrSelector;
    protected final CommandConsoleFactory consoleFactory;
    protected WorkProgressMonitor progressMonitor;
    protected long timeoutMS;

    protected CommandBuilder(final CommandConsoleFactory consoleFactory) {
        this.consoleFactory = consoleFactory;
        
        // set the initial setate
        this.reset();
    }
    
    public void reset() {
        this.consoleName = null;
        // Default to the current working directory
        this.directory = new File(System.getProperty("user.dir"));
        this.args = new ArrayList<>();
        this.stdout = null;
        this.stderr = null;
        this.stdoutSelector = null;
        this.stderrSelector = null;
        this.progressMonitor = null;
        
        // TODO make Bazel command timeout configurable
        this.timeoutMS = 100000; // default timeout
    }

    /**
     * Set the console name.
     *
     * <p>
     * The console name is used to print result of the program. Only lines not filtered by
     * {@link #setStderrLineSelector(Function)} and {@link #setStdoutLineSelector(Function)} are printed to the
     * console. If {@link #setStandardError(OutputStream)} or {@link #setStandardOutput(OutputStream)} have been
     * used with a non null value, then they intercept all output from being printed to the console.
     *
     * <p>
     * If name is null, no output is written to any console.
     */
    public CommandBuilder setConsoleName(String name) {
        this.consoleName = name;
        return this;
    }

    /**
     * Set the working directory for the program, it is set to the current working directory of the current java
     * process by default.
     */
    public CommandBuilder setDirectory(File directory) {
        this.directory = directory;
        return this;
    }

    /**
     * Set an {@link OutputStream} to receive non selected lines from the standard output stream of the program in
     * lieu of the console. If a selector has been set with {@link #setStdoutLineSelector(Function)}, only the lines
     * not selected (for which the selector returns null) will be printed to the {@link OutputStream}.
     */
    public CommandBuilder setStandardOutput(OutputStream stdout) {
        this.stdout = stdout;
        return this;
    }

    /**
     * Set an {@link OutputStream} to receive non selected lines from the standard error stream of the program in
     * lieu of the console. If a selector has been set with {@link #setStderrLineSelector(Function)}, only the lines
     * not selected (for which the selector returns null) will be printed to the {@link OutputStream}.
     */
    public CommandBuilder setStandardError(OutputStream stderr) {
        this.stderr = stderr;
        return this;
    }

    /**
     * Set an {@link OutputStreamObserver} to receive non selected lines from the standard output stream of the program in
     * line of the console. If a selector has been set with {@link #setStdoutLineSelector(Function)}, only the lines
     * not selected (for which the selector returns null) will be updated to the {@link OutputStreamObserver}.
     */
    public CommandBuilder setStandardOutObserver(OutputStreamObserver outputObserver) {
        this.stdoutObserver = outputObserver;
        return this;
    }
    
    /**
     * Set an {@link OutputStreamObserver} to receive non selected lines from the standard error stream of the program in
     * line of the console. If a selector has been set with {@link #setStderrLineSelector(Function)}, only the lines
     * not selected (for which the selector returns null) will be updated to the {@link OutputStreamObserver}.
     */
    public CommandBuilder setStandardErrorObserver(OutputStreamObserver errorObserver) {
        this.stderrObserver = errorObserver;
        return this;
    }

    /**
     * Add arguments to the command line. The first argument to be added to the builder is the program name.
     */
    public CommandBuilder addArguments(String... args) {
        this.args.addAll(Arrays.asList(args));
        return this;
    }

    /**
     * Add a list of arguments to the command line. The first argument to be added to the builder is the program
     * name.
     */
    public CommandBuilder addArguments(Iterable<String> args) {
        for (String arg : args) {
            this.args.add(arg);
        }
        return this;
    }

    /**
     * Set a selector to accumulate lines that are selected from the standard output stream.
     *
     * <p>
     * The selector is passed all lines that are printed to the standard output. It can either returns null to say
     * that the line should be passed to the console or to a non null value that will be stored. All values that
     * have been selected (for which the selector returns a non-null value) will be stored in a list accessible
     * through {@link Command#getSelectedOutputLines()}. The selected lines will not be printed to the console.
     */
    public CommandBuilder setStdoutLineSelector(Function<String, String> selector) {
        this.stdoutSelector = selector;
        return this;
    }

    /**
     * Set a selector to accumulate lines that are selected from the standard error stream.
     *
     * <p>
     * The selector is passed all lines that are printed to the standard error. It can either returns null to say
     * that the line should be passed to the console or to a non null value that will be stored. All values that
     * have been selected (for which the selector returns a non-null value) will be stored in a list accessible
     * through {@link Command#getSelectedErrorLines()}. The selected lines will not be printed to the console.
     */
    public CommandBuilder setStderrLineSelector(Function<String, String> selector) {
        this.stderrSelector = selector;
        return this;
    }

    /**
     * Provide an optional progress monitor.
     */
    public CommandBuilder setProgressMonitor(WorkProgressMonitor progressMonitor) {
        this.progressMonitor = progressMonitor;
        return this;
    }

    /**
     * Provide an optional timeout for the command (in milliseconds).
     */
    public CommandBuilder setTimeout(long timeoutMS) {
        this.timeoutMS = timeoutMS;
        return this;
    }

    /**
     * Build a Command object.
     */
    public Command build() throws IOException {
        Command command = null;
        try {
            command = this.build_impl();
        } finally {
            this.reset();
        }
        return command;
    }
    
    /**
     * Build the specific Command implementation object.
     */
    protected abstract Command build_impl() throws IOException;

}