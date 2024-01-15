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

import org.eclipse.core.runtime.OperationCanceledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.BazelJavaSdkPlugin;
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

    public static record PreparedCommandLine(
            List<String> fullCommandLineWithOptionalShellWrappingAndBinary,
            List<String> commandLineForDisplayPurposes) {
    }

    private static Logger LOG = LoggerFactory.getLogger(DefaultBazelCommandExecutor.class);
    private static ThreadGroup pipesThreadGroup;
    private static ProcessStreamsProvider SYSOUT_ERR_PROVIDER = new ProcessStreamsProvider();

    /**
     * This method is a workaround for https://github.com/salesforce/bazel-eclipse/issues/464.
     * <p>
     * For some reason the ThreadGroup gets automatically destroyed.
     * </p>
     *
     * @return the thread group
     */
    @SuppressWarnings("removal")
    private static synchronized ThreadGroup getPipesThreadGroup() {
        if ((pipesThreadGroup == null) || pipesThreadGroup.isDestroyed()) {
            // https://github.com/salesforce/bazel-eclipse/issues/464
            return pipesThreadGroup = new ThreadGroup("Bazel Command Executor Pipes");
        }

        return pipesThreadGroup;
    }

    protected static Thread pipe(final InputStream src, final OutputStream dest, String threadDetails) {
        final var thread = new Thread(getPipesThreadGroup(), (Runnable) () -> {
            // we don't close any streams as we expect this do be done outside
            try {
                var transfered = src.transferTo(dest);
                LOG.debug("Transfered {} bytes in pipe '{}'", transfered, threadDetails);
            } catch (final IOException e) {
                LOG.error(
                    "IO error while processing command output in pipe '{}': {}",
                    threadDetails,
                    e.getMessage(),
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

    private boolean wrapExecutionIntoShell = getSystemUtil().isMac(); // default is yes only on Mac to ensure proper path
    private final ShellUtil shellUtil = new ShellUtil(); // login shell change requires Eclipse restart
    private volatile Map<String, String> extraEnv;
    private volatile BazelBinary bazelBinary;
    protected volatile String cachedToolTagArgument;

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
        try (var streamProvider = newProcessStreamProvider(command, commandLine)) {
            // wrap execution into another try-catch to allow enriching the IOException when necessary
            try {
                // call provider hook
                streamProvider.beginExecution();

                // log the command line
                var fullCommandLine = processBuilder.command().stream().collect(joining(" "));
                LOG.debug(fullCommandLine);

                // redirect standard out (otherwise we will pipe to System.out after starting the process)
                if (command.getStdOutFile() != null) {
                    processBuilder.redirectOutput(command.getStdOutFile().toFile());
                }

                // start process
                final var process = processBuilder.start();

                // forward to console if not redirected to file
                final var p1 = command.getStdOutFile() == null
                        ? pipe(process.getInputStream(), streamProvider.getOutStream(), fullCommandLine) : null;
                final var p2 = pipe(process.getErrorStream(), streamProvider.getErrorStream(), fullCommandLine);

                try {
                    while (!process.waitFor(500L, TimeUnit.MILLISECONDS)) {
                        Thread.onSpinWait();
                        if (cancelationCallback.isCanceled()) {
                            process.destroyForcibly();
                            streamProvider.executionCanceled();
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

                var result = process.exitValue();

                // call provider hook
                streamProvider.executionFinished(result);

                // send result to command
                var commandResult = command.generateResult(result);

                // call provider hook
                streamProvider.commandResultGenerated(commandResult);

                return commandResult;
            } catch (IOException e) {
                streamProvider.executionFailed(e);
                throw e;
            }

        } catch (final InterruptedException e) {
            // ignore, just reset interrupt flag
            Thread.currentThread().interrupt();
            throw new OperationCanceledException("Aborted waiting for result");
        }

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
     * {@return the <code>--tool_tag=...</code> argument to be added to every Bazel command}
     */
    protected String getToolTagArgument() {
        var toolTagArgument = this.cachedToolTagArgument;
        if (toolTagArgument != null) {
            return toolTagArgument;
        }
        return this.cachedToolTagArgument = format("--tool_tag=java:sdk:%s", BazelJavaSdkPlugin.getBundleVersion());
    }

    /**
     * Hook to inject additional options into the command line.
     * <p>
     * Called by {@link #prepareCommandLine(BazelCommand)} before the Bazel binary or any shell wrapping is added.
     * Default implementation adds the <code>--tool_tag</code> argument. Subclasses should call <code>super</code> to
     * retain that behavior.
     * </p>
     *
     * @param commandLine
     *            the command line to manipulate (never <code>null</code>)
     * @param injectPositionForNoneStartupOptions
     *            the position to inject options which must come afte the Bazel command (eg., build/query/run/etc.)
     */
    protected void injectAdditionalOptions(List<String> commandLine, int injectPositionForNoneStartupOptions) {
        // add --tool_tag
        commandLine.add(injectPositionForNoneStartupOptions, getToolTagArgument());
    }

    public boolean isWrapExecutionIntoShell() {
        return wrapExecutionIntoShell;
    }

    protected ProcessBuilder newProcessBuilder(List<String> commandLine) throws IOException {
        return new ProcessBuilder(commandLine);
    }

    /**
     * Called by
     * {@link #doExecuteProcess(BazelCommand, com.salesforce.bazel.sdk.command.BazelCommandExecutor.CancelationCallback, ProcessBuilder, PreparedCommandLine)}
     * to create a new process stream provider.
     * <p>
     * The default implementation returns a static instance providing access to {@link System#out}/{@link System#err}.
     * Subclasses may override to provide a more sophisticated implementation (eg., redirected output to an IDE built-in
     * console).
     * </p>
     *
     * @param command
     *            the command to be executed
     * @param commandLine
     *            the command line
     * @return the {@link ProcessStreamsProvider} instance (must not be <code>null</code>)
     * @throws IOException
     *             in case of errors creating/opening the underlying streams
     */
    protected ProcessStreamsProvider newProcessStreamProvider(BazelCommand<?> command, PreparedCommandLine commandLine)
            throws IOException {
        return SYSOUT_ERR_PROVIDER;
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
        // build command line
        var bazelBinary = command.ensureBazelBinary();
        var commandLine = command.prepareCommandLine(bazelBinary.bazelVersion());

        // create a copy (for manipulation)
        var fullCommandLine = new ArrayList<>(commandLine);

        // inject options required by executor implementation
        if (command.supportsInjectionOfAdditionalBazelOptions()) {
            // options such as --tool_tag need to come after the Bazel command (build/run/query/etc.)
            // we rely on the default implementation for BazelCommand#prepareCommandLine that the command is first item after startup arguments
            var injectPosition = command.getStartupArgs().size() + 1;

            injectAdditionalOptions(fullCommandLine, injectPosition);
        }

        // the binary must be the first argument
        fullCommandLine.add(0, bazelBinary.executable().toString());

        // add the short binary name to the visible command line
        commandLine.add(0, bazelBinary.executable().getFileName().toString());

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
