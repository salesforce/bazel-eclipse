package com.salesforce.bazel.eclipse.ui.execution;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_BOLD;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_FAINT;
import static org.fusesource.jansi.Ansi.Attribute.ITALIC;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.extensions.EclipseHeadlessBazelCommandExecutor;
import com.salesforce.bazel.eclipse.ui.BazelUIPlugin;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelCommandExecutor;

/**
 * Implementation of a {@link BazelCommandExecutor} which integrates with the Eclipse Console.
 */
public class EclipseConsoleBazelCommandExecutor extends EclipseHeadlessBazelCommandExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(EclipseConsoleBazelCommandExecutor.class);

    private static final Predicate<String> errorPrefixFilter = (var s) -> s.startsWith("ERROR:");

    static String humanReadableFormat(Duration duration) {
        return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
    }

    @Override
    protected <R> R doExecuteProcess(BazelCommand<R> command, CancelationCallback cancelationCallback,
            ProcessBuilder processBuilder, PreparedCommandLine commandLine) throws IOException {

        var console = findConsole(format("Bazel Workspace (%s)", command.getWorkingDirectory()));
        showConsole(console);

        try (final var consoleStream = console.newMessageStream();
                final var errorStream = new CapturingLiniesAndForwardingOutputStream(
                        consoleStream,
                        Charset.defaultCharset(),
                        4,
                        errorPrefixFilter)) {
            // remove old output
            //console.clearConsole();
            consoleStream.println();

            // print info about command
            var purpose = command.getPurpose() != null ? format(": %s", command.getPurpose()) : "";
            var startTime = new Date();
            consoleStream.println(ansi().a(ITALIC).a(startTime.toString()).a(purpose).reset().toString());
            consoleStream.println(ansi().a(INTENSITY_BOLD).a("Running Command:").reset().toString());
            consoleStream.println(
                " > bazel " + commandLine.commandLineWithoutBinaryAsPreparedByCommand().stream().collect(joining(" ")));
            consoleStream.println();

            var fullCommandLine = processBuilder.command().stream().collect(joining(" "));
            LOG.debug(fullCommandLine);

            int result;
            try {
                // redirect standard out (otherwise we will pipe to System.out after starting the process)
                if (command.getStdOutFile() != null) {
                    processBuilder.redirectOutput(command.getStdOutFile().toFile());
                }

                // start process
                final var process = processBuilder.start();

                // forward to console if not redirected to file
                final var p1 = command.getStdOutFile() == null
                        ? pipe(process.getInputStream(), consoleStream, fullCommandLine) : null;
                final var p2 = pipe(process.getErrorStream(), errorStream, fullCommandLine);

                try {
                    while (!process.waitFor(500L, TimeUnit.MILLISECONDS)) {
                        Thread.onSpinWait();
                        if (cancelationCallback.isCanceled()) {
                            process.destroyForcibly();
                            consoleStream.println();
                            consoleStream.print(
                                ansi().fgBrightMagenta()
                                        .a(INTENSITY_FAINT)
                                        .a("Operation cancelled!")
                                        .reset()
                                        .toString());
                            throw new OperationCanceledException("user cancelled");
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

                var endTime = new Date();
                var executionTime = Duration
                        .between(Instant.ofEpochMilli(startTime.getTime()), Instant.ofEpochMilli(endTime.getTime()));
                consoleStream.println();
                consoleStream.println(
                    ansi().a(ITALIC)
                            .a("Process finished in ")
                            .a(humanReadableFormat(executionTime))
                            .a(" (at ")
                            .a(endTime.toString())
                            .a(")")
                            .reset()
                            .toString());

                if (result != 0) {
                    consoleStream
                            .println(ansi().fgBrightBlack().a("Process exited with ").a(result).reset().toString());
                }
            } catch (final InterruptedException e) {
                // ignore, just reset interrupt flag
                Thread.currentThread().interrupt();
                throw new OperationCanceledException("user cancelled");
            } catch (final IOException e) {
                consoleStream.println(ansi().fgBrightRed().a("Command Execution Failed!").reset().toString());
                consoleStream.println(ansi().fgRed().a(e.getMessage()).reset().toString());
                consoleStream.println();
                e.printStackTrace(new PrintWriter(consoleStream));
                throw new OperationCanceledException("operation failed: " + e.getMessage());
            }

            // send result to command
            try {
                return command.generateResult(result);
            } catch (IOException e) {
                errorStream.close();
                var stderrLinesFiltered = errorStream.getCapturedLinesFiltered()
                        .stream()
                        .map(s -> s.replaceAll("\u001B\\[[;\\d]*m", ""))
                        .collect(joining(System.lineSeparator()));
                var stderrLines = errorStream.getCapturedLines()
                        .stream()
                        .map(s -> s.replaceAll("\u001B\\[[;\\d]*m", ""))
                        .collect(joining(System.lineSeparator()));
                if (stderrLines.length() > 0) {
                    throw new IOException(
                            format(
                                "%s%n---- Begin of Captured Error Output (last %d lines)----%n%s---- (more context) ----%n%s---- End of Captured Error Output ----%n",
                                e.getMessage(),
                                errorStream.getLinesToCapture(),
                                stderrLinesFiltered,
                                stderrLines),
                            e);
                }
                throw new IOException(format("%s%n(no error output was captured)", e.getMessage()), e);
            }
        }
    }

    MessageConsole findConsole(final String consoleName) {
        final var consoleManager = ConsolePlugin.getDefault().getConsoleManager();
        for (final IConsole existing : consoleManager.getConsoles()) {
            if (consoleName.equals(existing.getName())) {
                return (MessageConsole) existing;
            }
        }

        // no console found, so create a new one
        final var console = new MessageConsole(consoleName, getImageDescriptoForConsole());
        consoleManager.addConsoles(new IConsole[] { console });
        return console;
    }

    ImageDescriptor getImageDescriptoForConsole() {
        return BazelUIPlugin.getDefault().getImageRegistry().getDescriptor(BazelUIPlugin.ICON_BAZEL);
    }

    @Override
    protected String getToolTagArgument() {
        var toolTagArgument = this.cachedToolTagArgument;
        if (toolTagArgument != null) {
            return toolTagArgument;
        }
        return this.cachedToolTagArgument = format("--tool_tag=eclipse:ui:%s", BazelUIPlugin.getBundleVersion());
    }

    @Override
    protected void injectAdditionalOptions(List<String> commandLine) {
        super.injectAdditionalOptions(commandLine);

        // tweak for Eclipse Console
        commandLine.add("--color=yes");
        commandLine.add("--curses=no");
        commandLine.add("--progress_in_terminal_title=no");
    }

    private void showConsole(MessageConsole console) {
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
    }
}
