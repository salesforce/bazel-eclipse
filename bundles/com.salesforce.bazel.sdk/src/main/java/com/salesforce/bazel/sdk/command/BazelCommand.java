package com.salesforce.bazel.sdk.command;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.BazelVersion;

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
 * <li>Receive exit code and execution details from executor</li>
 * <li>Generate results</li>
 * <li>Garbage collect / throw away (short lived)</li>
 * </ol>
 * It's expected that the command is only modified till its executed. Command objects must not be hold on for a longer
 * time.
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
    private BazelBinary bazelBinary;

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
     * {@return the Bazel binary to use (never <code>null</code>)
     */
    BazelBinary ensureBazelBinary() {
        return requireNonNull(getBazelBinary(), "no Bazel binary configured; check the workflow");
    }

    /**
     * Called by {@link BazelCommandExecutor} to populate the command with the result of the process execution and
     * generate the result.
     *
     * @param exitCode
     *            exit code
     * @return the command result
     */
    public abstract R generateResult(int exitCode) throws IOException;

    /**
     * {@return the optional Bazel binary to use for executing this command (maybe <code>null</code>)}
     */
    public BazelBinary getBazelBinary() {
        return bazelBinary;
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
        var args = commandArgs;
        return args != null ? args : emptyList();
    }

    /**
     * @return the startup args
     */
    protected final List<String> getStartupArgs() {
        var args = startupArgs;
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
     * <p>
     * The Bazel binary will be added by {@link BazelCommandExecutor} and must not be included in the returned list.
     * </p>
     *
     * @param bazelVersion
     *            version of the Bazel binary (may impact flags to use)
     * @return the command line
     * @throws IOException
     *             in case of issues preparing the command line
     */
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        var commandLine = new ArrayList<>(getStartupArgs());
        commandLine.add(getCommand());
        commandLine.addAll(getCommandArgs());
        return commandLine;
    }

    /**
     * Sets an optional {@link BazelBinary} to use.
     * <p>
     * If a Bazel binary is set, the {@link BazelCommandExecutor} will prefer this one. Otherwise it will use a default.
     * </p>
     *
     * @param bazelBinary
     *            the Bazel binary to use for this command (maybe <code>null</code> to use a default)
     */
    public void setBazelBinary(BazelBinary bazelBinary) {
        this.bazelBinary = bazelBinary;
    }

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
        commandLine.add(getClass().getSimpleName());
        commandLine.addAll(getStartupArgs());
        commandLine.add(getCommand());
        commandLine.addAll(getCommandArgs());
        return commandLine.stream().map(s -> s.contains(" ") ? "\"" + s + "\"" : s).collect(joining(" "));
    }
}
