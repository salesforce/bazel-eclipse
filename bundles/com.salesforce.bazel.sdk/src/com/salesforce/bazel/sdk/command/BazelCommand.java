package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
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
        this.workingDirectory = requireNonNull(
            workingDirectory,
            "No working directory provided; Bazel needs to be executed from within a Bazel workspace");
    }

    protected void appendToStringDetails(ArrayList<String> toStringCommandLine) {
        toStringCommandLine.addAll(getStartupArgs());
        toStringCommandLine.add(getCommand());
        toStringCommandLine.addAll(getCommandArgs());
        toStringCommandLine.add("[" + getClass().getSimpleName() + "]");
    }

    /**
     * Called by {@link #generateResult(int)} when the exit code is zero.
     * <p>
     * Implementors are expected to read the command output (either {@link #getStdOutFile()} or some other output
     * produced by the command) and process is into the desired result.
     *
     * @return the command result (never <code>null</code>)
     * @throws IOException
     */
    protected abstract R doGenerateResult() throws IOException;

    /**
     * {@return the Bazel binary to use (never <code>null</code>)
     */
    BazelBinary ensureBazelBinary() {
        return requireNonNull(
            getBazelBinary(),
            "no Bazel binary configured; check code logic - it should be set to a default by the executor");
    }

    /**
     * Called by {@link BazelCommandExecutor} to populate the command with the result of the process execution and
     * generate the result.
     * <p>
     * The default implementation checks the exit code and if ok, calls {@link #doGenerateResult()}. If the exit code is
     * none zero a failure will be raised.
     * </p>
     *
     * @param exitCode
     *            exit code
     * @return the command result
     * @throws IOException
     *             if the command execution failed in a way where no output could be produced
     */
    public R generateResult(int exitCode) throws IOException {
        if (exitCode != 0) {
            throw new IOException(
                    format("Bazel %s failed with exit code %d. Please check command output.", getCommand(), exitCode));
        }

        return requireNonNull(
            doGenerateResult(),
            () -> format("Invalid command implementation '%s'. null result not allowed", BazelCommand.this.getClass()));
    }

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
     * <p>
     * Implementors can assume that this method is called at most once during the lifecycle of a command. This is
     * important so that temporary files can be created without complicating the implementation.
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
        if (workingDirectory != null) {
            commandLine.add(workingDirectory.getFileName().toString() + ">");
        }
        if (bazelBinary != null) {
            commandLine.add(bazelBinary.executable().getFileName().toString());
        } else {
            commandLine.add("bazel");
        }
        appendToStringDetails(commandLine);
        return commandLine.stream().map(s -> s.contains(" ") ? "\"" + s + "\"" : s).collect(joining(" "));
    }
}
