package com.salesforce.bazel.sdk.command;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A rich data structure for defining commands and parsing their output.
 * <p>
 * Bazel commands will be executed using a {@link BazelCommandExecutor}. Bazel commands are intended to be stateful.
 * Reusing commands across multiple invocation is an unsupported use case and might result in unexpected behavior.
 * </p>
 * <p>
 * The general lifecycle of command instance is the following:
 * <ol>
 * <li>Create command object instance</li>
 * <li>Set values for command execution</li>
 * <li>Execute command using {@link BazelCommandExecutor}</li>
 * <li>Receive result and execution details from executor</li>
 * <li>Read results from command object</li>
 * </ol>
 * It's expected that the command is only modified till its executed.
 * </p>
 *
 * @param <R>
 *            the command result
 */
public abstract class BazelCommand<R> {

    private final String command;
    private final Path workingDirectory;

    private List<String> startupArgs;
    private List<String> commandArgs;
    private Path stdOutFile;

    /**
     * Creates a command using the specified command.
     * <p>
     * See <code>bazel help</code> for list of command.
     * </p>
     *
     * @param command
     *            the command (must not be <code>null</code>)
     * @param workingDirectory
     *            the command working directory (typically the workspace root, must not be <code>null</code>)
     */
    public BazelCommand(String command, Path workingDirectory) {
        this.command = requireNonNull(command, "No command provided; see 'bazel help' for available commands");
        this.workingDirectory = requireNonNull(workingDirectory,
            "No working directory provided; Bazel needs to be executed from within a Bazel workspace");
    }

    /**
     * @return the Bazel command
     */
    protected final String getCommand() {
        return command;
    }

    /**
     * @return the command args
     */
    protected final List<String> getCommandArgs() {
        List<String> args = commandArgs;
        return args != null ? args : emptyList();
    }

    /**
     * @return the startup args
     */
    protected final List<String> getStartupArgs() {
        List<String> args = startupArgs;
        return args != null ? args : emptyList();
    }

    /**
     * @return Path to file for redirecting stdout
     */
    public Path getStdOutFile() {
        return stdOutFile;
    }

    /**
     * @return the working directory
     */
    public final Path getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * Called by {@link BazelCommandExecutor} to assemble the command line (excluding the Bazel binary).
     * <p>
     * The default implementation just combines startup arguments + command + command arguments into a single list.
     * Subclasses may override to do some additional processing.
     * </p>
     * <p>
     * Note, the returned list is guaranteed to be modifiable.
     * </p>
     *
     * @return the command line
     * @throws IOException
     *             in case of issues preparing the command line
     */
    public List<String> prepareCommandLine() throws IOException {
        var commandLine = new ArrayList<String>();
        commandLine.addAll(getStartupArgs());
        commandLine.add(getCommand());
        commandLine.addAll(getCommandArgs());
        return commandLine;
    }

    /**
     * Called by {@link BazelCommandExecutor} to populate the command with the result of the process execution and generate the result.
     *
     * @param exitCode exit code
     * @param stdout output of stdout (<code>null</code> in case {@link #getStdOutFile()} was set when executing the command)
     * @param stderr output of stderr
     * @return the command result
     */
    public abstract R processResult(int exitCode, String stdout, String stderr) throws IOException;

    /**
     * Sets the command arguments to use for this command.
     * <p>
     * command arguments are appended to the Bazel command line after <code>command</code>.
     * </p>
     *
     * @param args
     *            the command arguments
     */
    protected void setCommandArgs(String... args) {
        commandArgs = args != null ? List.of(args) : emptyList();
    }

    /**
     * Configures the command to redirect stdout into a file.
     * <p>
     * This is useful when the output is a streamed or serialized format (eg. <code>--output streamed_proto</code> or
     * <code>--output proto</code> with <code>bazel query</code>).
     * </p>
     *
     * @param stdOutFile
     *            the file to write output to
     */
    protected void setRedirectStdOutToFile(Path stdOutFile) {
        this.stdOutFile = stdOutFile;
    }

    /**
     * Sets the startup arguments to use for this command.
     * <p>
     * Startup arguments are injected into the Bazel command line between <code>bazel</code> and <code>command</code>.
     * </p>
     *
     * @param args
     *            the startup arguments
     */
    protected void setStartupArgs(String... args) {
        startupArgs = args != null ? List.of(args) : emptyList();
    }

    @Override
    public String toString() {
        var commandLine = new ArrayList<String>();
        commandLine.add("bazel");
        commandLine.addAll(getStartupArgs());
        commandLine.add(getCommand());
        commandLine.addAll(getCommandArgs());
        return commandLine.stream().map(s -> s.contains(" ") ? "\"" + s + "\"" : s).collect(joining(" "));
    }
}
