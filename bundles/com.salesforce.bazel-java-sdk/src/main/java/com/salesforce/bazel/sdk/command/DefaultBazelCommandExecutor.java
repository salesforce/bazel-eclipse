package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.nio.file.Files.newOutputStream;
import static java.util.stream.Collectors.joining;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.command.shell.MacOsLoginShellFinder;
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

    private static Logger LOG = LoggerFactory.getLogger(DefaultBazelCommandExecutor.class);

    private static final ThreadGroup pipesThreadGroup = new ThreadGroup("Bazel Command Executor Pipes");

    protected static Thread pipe(final InputStream src, final OutputStream dest, String threadDetails) {
        final Thread thread = new Thread(pipesThreadGroup, (Runnable) () -> {
            // we don't close any streams as we expect this do be done outside
            try {
                src.transferTo(dest);
            } catch (final IOException e) {
                // ignore
            }
        }, format("Bazel Command Executor Pipe (%s)", threadDetails));
        thread.setDaemon(true);
        thread.start();

        return thread;
    }

    private boolean wrapExecutionIntoShell = !getSystemUtil().isWindows(); // default is yes except on Windows
    private volatile Path detectedShell;
    private volatile Map<String, String> extraEnv;

    protected Path detectShell() throws IOException {
        if (getSystemUtil().isWindows())
            return null; // not supported

        Path shell = detectedShell;
        if (shell != null)
            return shell;

        synchronized (this) {
            if (getSystemUtil().isMac())
                return detectedShell = new MacOsLoginShellFinder().detectLoginShell();
            else if (getSystemUtil().isUnix())
                return detectedShell = new MacOsLoginShellFinder().detectLoginShell();
            else
                throw new IOException("Unsupported OS: " + getSystemUtil().getOs());
        }
    }

    protected <R> R doExecuteProcess(BazelCommand<R> command, CancelationCallback cancelationCallback,
            ProcessBuilder processBuilder) throws IOException {
        // capture stdout and stderr
        OutputStream out = command.getStdOutFile() != null ? newOutputStream(command.getStdOutFile())
                : new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        // execute
        final int result;
        try {
            String fullCommandLine = processBuilder.command().stream().collect(joining(" "));
            LOG.debug(fullCommandLine);

            final Process process = processBuilder.start();

            final Thread p1 = pipe(process.getInputStream(), out, fullCommandLine);
            final Thread p2 = pipe(process.getErrorStream(), err, fullCommandLine);

            try {
                while (!process.waitFor(500L, TimeUnit.MILLISECONDS)) {
                    if (cancelationCallback.isCanceled()) {
                        process.destroyForcibly();
                        throw new IOException("user cancelled");
                    }
                }
            } finally {
                // interrupt pipe threads so they'll die
                p1.interrupt();
                p2.interrupt();
            }

            result = process.exitValue();
        } catch (final InterruptedException e) {
            // ignore, just reset interrupt flag
            Thread.currentThread().interrupt();
            throw new IOException("Aborted waiting for result");
        }

        // set result to command
        String stdout = null;
        if (out instanceof ByteArrayOutputStream) {
            stdout = out.toString();
        }
        return command.processResult(result, stdout, err.toString());
    }

    @Override
    public <R> R execute(BazelCommand<R> command, CancelationCallback cancelationCallback) throws IOException {
        // full command line
        List<String> commandLine = prepareCommandLine(command);

        // start building the process
        ProcessBuilder processBuilder = newProcessBuilder(commandLine);

        // run command in workspace
        processBuilder.directory(command.getWorkingDirectory().toFile());

        // extra environment variables
        Map<String, String> extraEnv = this.extraEnv;
        if (extraEnv != null) {
            processBuilder.environment().putAll(extraEnv);
        }

        return doExecuteProcess(command, cancelationCallback, processBuilder);

    }

    public Map<String, String> getExtraEnv() {
        return extraEnv;
    }

    SystemUtil getSystemUtil() {
        return SystemUtil.getInstance();
    }

    public boolean isWrapExecutionIntoShell() {
        return wrapExecutionIntoShell;
    }

    protected ProcessBuilder newProcessBuilder(List<String> commandLine) throws IOException {
        return new ProcessBuilder(commandLine);
    }

    protected <R> List<String> prepareCommandLine(BazelCommand<R> command) throws IOException {
        List<String> commandLine = command.prepareCommandLine();

        if (isWrapExecutionIntoShell()) {
            Path shell = detectShell();
            if (shell != null) {
                return switch (shell.getFileName().toString()) {
                    case "fish", "zsh", "bash" -> getSystemUtil().isMac() // login shell on Mac
                            ? List.of(shell.toString(), "-l", "-c", toQuotedStringForShell(commandLine))
                            : List.of(shell.toString(), "-c", toQuotedStringForShell(commandLine));
                    default -> throw new IOException("Unsupported shell: " + shell);
                };
            }
            throw new IOException("Unable to wrap in shell. None detected!");
        }

        return commandLine;
    }

    public void setExtraEnv(Map<String, String> extraEnv) {
        this.extraEnv = extraEnv;
    }

    public void setWrapExecutionIntoShell(boolean wrapExecutionIntoShell) {
        this.wrapExecutionIntoShell = wrapExecutionIntoShell;
    }

    protected String toQuotedStringForShell(List<String> commandLine) {
        StringBuilder result = new StringBuilder();
        for (String arg : commandLine) {
            if (result.length() > 0)
                result.append(' ');
            boolean quoteArg = arg.indexOf(' ') > -1 && !arg.startsWith("\\\"");
            if (quoteArg)
                result.append("\"");
            result.append(arg.replace("\"", "\\\""));
            if (quoteArg)
                result.append("\"");
        }
        return result.toString();
    }

}
