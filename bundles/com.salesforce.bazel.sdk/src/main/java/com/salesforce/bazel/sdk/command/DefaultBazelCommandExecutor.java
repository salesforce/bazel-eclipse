package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.command.shell.ShellUtil;
import com.salesforce.bazel.sdk.util.SystemUtil;

/**
 * Default implementation of {@link BazelCommandExecutor}.
 * <p>
 * The executor uses {@link ProcessBuilder} to create {@link Process processes} for Bazel. It can be configure to a
 * specific Bazel binary location. By default it expects to find <code>bazel</code> binary on the system path.
 * </p>
 * <p>
 * In order to ensure the created processes are as close as possible to the system terminal it's possible to wrap Bazel
 * invocations into a shell. The shell will be used to establish a shell environment. They may only be needed when the
 * executor is uses in an environment not matching a typical shell environment. For example, MacOS GUIs won't have a
 * shell environment. Thus, lots of items are not properly setup.
 * </p>
 */
public class DefaultBazelCommandExecutor implements BazelCommandExecutor {

    protected static record PreparedCommandLine(
            List<String> fullCommandLineWithOptionalShellWrappingAndBinary,
            List<String> commandLineWithoutBinaryAsPreparedByCommand) {
    }

    private static Logger LOG = LoggerFactory.getLogger(DefaultBazelCommandExecutor.class);

    private static final ThreadGroup pipesThreadGroup = new ThreadGroup("Bazel Command Executor Pipes");

    protected static Thread pipe(final InputStream src, final OutputStream dest, String threadDetails) {
        final var thread = new Thread(pipesThreadGroup, (Runnable) () -> {
            // we don't close any streams as we expect this do be done outside
            try {
                var transfered = src.transferTo(dest);
                LOG.debug("Transfered {} bytes in pipe '{}'", transfered, threadDetails);
            } catch (final IOException e) {
                LOG.error("IO error while processing command output in pipe '{}': {}", threadDetails, e.getMessage(),
                    e);
            }
        }, format("Bazel Command Executor Pipe (%s)", threadDetails));
        thread.setDaemon(true);
        thread.start();

        return thread;
    }

    protected static void waitForPipeToFinish(Thread pipe, CancelationCallback cancelationCallback)
            throws IOException, InterruptedException {
        var delay = 0L;
        var sleepTime = 100L;
        while (pipe.isAlive() && !cancelationCallback.isCanceled()) {
            Thread.onSpinWait();
            Thread.sleep(sleepTime);
            if ((delay += sleepTime) >= 60000L) {
                throw new IOException(
                        format("Pipe '%s' did not finish writing within expected timeout!", pipe.getName()));
            }
        }
    }

    private boolean wrapExecutionIntoShell = !getSystemUtil().isWindows(); // default is yes except on Windows
    private final ShellUtil shellUtil = new ShellUtil(); // login shell change requires Eclipse restart
    private volatile Map<String, String> extraEnv;
    private volatile BazelBinary bazelBinary;

    /**
     * Detects the binary to use.
     * <p>
     * The default implementation checks {@link BazelCommand#getBazelBinary()} and falls back to
     * {@link #getBazelBinary()} if the command does not request a custom binary.
     * </p>
     * <p>
     * Once the proper binary is detected the command will be updated.
     * </p>
     *
     * @param command
     *            the command (must not be <code>null</code>)
     * @return the Bazel binary to use for launching the command (never <code>null</code>)
     */
    protected void configureBazelBinary(BazelCommand<?> command) {
        var binary = Optional.ofNullable(command.getBazelBinary()).orElseGet(this::getBazelBinary);
        // ensure command has the proper binary
        command.setBazelBinary(binary);
    }

    protected <R> R doExecuteProcess(BazelCommand<R> command, CancelationCallback cancelationCallback,
            ProcessBuilder processBuilder, PreparedCommandLine commandLine) throws IOException {
        // execute
        final int result;
        try {
            var fullCommandLine = processBuilder.command().stream().collect(joining(" "));
            LOG.debug(fullCommandLine);

            // redirect standard out (otherwise we will pipe to System.out after starting the process)
            if (command.getStdOutFile() != null) {
                processBuilder.redirectOutput(command.getStdOutFile().toFile());
            }

            // start process
            final var process = processBuilder.start();

            // forward to console if not redirected to file
            final var p1 = command.getStdOutFile() == null ? pipe(process.getInputStream(), System.out, fullCommandLine)
                    : null;
            final var p2 = pipe(process.getErrorStream(), getProcessErrorStream(), fullCommandLine);

            try {
                while (!process.waitFor(500L, TimeUnit.MILLISECONDS)) {
                    Thread.onSpinWait();
                    if (cancelationCallback.isCanceled()) {
                        process.destroyForcibly();
                        throw new IOException("user cancelled");
                    }
                }

                // wait for pipes to finish
                if (p1 != null) {
                    waitForPipeToFinish(p1, cancelationCallback);
                }
                waitForPipeToFinish(p2, cancelationCallback);
            } finally {
                // interrupt pipe threads so they'll die
                if (p1 != null) {
                    p1.interrupt();
                }
                p2.interrupt();
            }

            result = process.exitValue();
        } catch (final InterruptedException e) {
            // ignore, just reset interrupt flag
            Thread.currentThread().interrupt();
            throw new IOException("Aborted waiting for result");
        }

        // send result to command
        return command.generateResult(result);
    }

    @Override
    public <R> R execute(BazelCommand<R> command, CancelationCallback cancelationCallback) throws IOException {
        // configure binary
        configureBazelBinary(command);

        // full command line
        var commandLine = prepareCommandLine(command);

        // start building the process
        var processBuilder = newProcessBuilder(commandLine.fullCommandLineWithOptionalShellWrappingAndBinary());

        // run command in workspace
        processBuilder.directory(command.getWorkingDirectory().toFile());

        // extra environment variables
        var extraEnv = this.extraEnv;
        if (extraEnv != null) {
            processBuilder.environment().putAll(extraEnv);
        }

        return doExecuteProcess(command, cancelationCallback, processBuilder, commandLine);

    }

    @Override
    public BazelBinary getBazelBinary() {
        return requireNonNull(bazelBinary, "no Bazel binary set");
    }

    public Map<String, String> getExtraEnv() {
        return extraEnv;
    }

    /**
     * Hook for sub classes to use a different stream to which the process' STDERR output should go to.
     * <p>
     * Note, the stream will never be closed.
     * </p>
     *
     * @return the stream for stderr, defaults to {@link System#err}
     */
    protected OutputStream getProcessErrorStream() {
        return System.err;
    }

    protected ShellUtil getShellUtil() {
        return shellUtil;
    }

    /**
     * The {@link SystemUtil} instance to use
     *
     * @return {@link SystemUtil#getInstance()}
     */
    protected SystemUtil getSystemUtil() {
        return SystemUtil.getInstance();
    }

    /**
     * Hook to inject additional options into the command line.
     * <p>
     * Called by {@link #prepareCommandLine(BazelCommand)} before the Bazel binary or any shell wrapping is added.
     * Default implementation does nothing.
     * </p>
     *
     * @param commandLine
     *            the command line to manipulate (never <code>null</code>)
     */
    protected void injectAdditionalOptions(List<String> commandLine) {
        // empty
    }

    public boolean isWrapExecutionIntoShell() {
        return wrapExecutionIntoShell;
    }

    protected ProcessBuilder newProcessBuilder(List<String> commandLine) throws IOException {
        return new ProcessBuilder(commandLine);
    }

    /**
     * Prepares the full command line including the Bazel binary as well as any necessary shell wrapping.
     * <p>
     * Called by
     * {@link #execute(BazelCommand, com.salesforce.bazel.sdk.command.BazelCommandExecutor.CancelationCallback)} after
     * the binary has been detected.
     * </p>
     * <p>
     * Note, this command is called after the binary has been configured on the command.
     * </p>
     *
     * @param command
     *            the command (must not be <code>null</code>)
     * @return the full command line to pass on to {@link ProcessBuilder} (never <code>null</code>)
     * @throws IOException
     *             in case of IO issues creating temporary files or other resources required for command execution
     */
    protected PreparedCommandLine prepareCommandLine(BazelCommand<?> command) throws IOException {
        var bazelBinary = command.ensureBazelBinary();
        var commandLine = command.prepareCommandLine(bazelBinary.bazelVersion());

        var fullCommandLine = new ArrayList<>(commandLine);

        injectAdditionalOptions(fullCommandLine);

        fullCommandLine.add(0, bazelBinary.executable().toString());

        if (isWrapExecutionIntoShell()) {
            return new PreparedCommandLine(getShellUtil().wrapExecutionIntoShell(fullCommandLine), commandLine);
        }

        return new PreparedCommandLine(fullCommandLine, commandLine);
    }

    public void setBazelBinary(BazelBinary bazelBinary) {
        this.bazelBinary = bazelBinary;
    }

    public void setExtraEnv(Map<String, String> extraEnv) {
        this.extraEnv = extraEnv;
    }

    public void setWrapExecutionIntoShell(boolean wrapExecutionIntoShell) {
        this.wrapExecutionIntoShell = wrapExecutionIntoShell;
    }

}
