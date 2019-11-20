package com.salesforce.bazel.eclipse.command;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;

/**
 * A builder class to generate a Command object.
 */
public abstract class CommandBuilder {

    protected String consoleName = null;
    protected File directory;
    protected ImmutableList.Builder<String> args = ImmutableList.builder();
    protected OutputStream stdout = null;
    protected OutputStream stderr = null;
    protected Function<String, String> stdoutSelector;
    protected Function<String, String> stderrSelector;
    protected final CommandConsoleFactory consoleFactory;
    protected WorkProgressMonitor progressMonitor;

    protected CommandBuilder(final CommandConsoleFactory consoleFactory) {
        this.consoleFactory = consoleFactory;
        this.reset();
    }
    
    public void reset() {
        // TODO redo the CommandBuilder so an explicit reset is not necessary
        this.consoleName = null;
        // Default to the current working directory
        this.directory = new File(System.getProperty("user.dir"));
        this.args = ImmutableList.builder();
        this.stdout = null;
        this.stderr = null;
        this.stdoutSelector = null;
        this.stderrSelector = null;
        this.progressMonitor = null;
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
     * Add arguments to the command line. The first argument to be added to the builder is the program name.
     */
    public CommandBuilder addArguments(String... args) {
        this.args.add(args);
        return this;
    }

    /**
     * Add a list of arguments to the command line. The first argument to be added to the builder is the program
     * name.
     */
    public CommandBuilder addArguments(Iterable<String> args) {
        this.args.addAll(args);
        return this;
    }

    /**
     * Set a selector to accumulate lines that are selected from the standard output stream.
     *
     * <p>
     * The selector is passed all lines that are printed to the standard output. It can either returns null to say
     * that the line should be passed to the console or to a non null value that will be stored. All values that
     * have been selected (for which the selector returns a non-null value) will be stored in a list accessible
     * through {@link ShellCommand#getSelectedOutputLines()}. The selected lines will not be printed to the console.
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
     * through {@link ShellCommand#getSelectedErrorLines()}. The selected lines will not be printed to the console.
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
     * Build a Command object.
     */
    public abstract Command build() throws IOException;
}