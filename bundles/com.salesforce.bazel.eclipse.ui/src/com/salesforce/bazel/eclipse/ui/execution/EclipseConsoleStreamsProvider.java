package com.salesforce.bazel.eclipse.ui.execution;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_BOLD;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_FAINT;
import static org.fusesource.jansi.Ansi.Attribute.ITALIC;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Predicate;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.salesforce.bazel.eclipse.ui.BazelUIPlugin;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor.PreparedCommandLine;
import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor.ProcessStreamsProvider;

/**
 * A specialized {@link ProcessStreamsProvider} interacting with the Eclipse Console
 */
public class EclipseConsoleStreamsProvider extends ProcessStreamsProvider {

    private static final Predicate<String> errorPrefixFilter = (var s) -> s.startsWith("ERROR:");

    static String humanReadableFormat(Duration duration) {
        return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
    }

    private final MessageConsoleStream consoleStream;
    private final CapturingLiniesAndForwardingOutputStream errorStream;
    private final BazelCommand<?> command;
    private final PreparedCommandLine commandLine;
    private final Date startTime;

    public EclipseConsoleStreamsProvider(BazelCommand<?> command, PreparedCommandLine commandLine) throws IOException {
        this.command = command;
        this.commandLine = commandLine;

        var console = findConsole(format("Bazel Workspace (%s)", command.getWorkingDirectory()));
        showConsole(console);

        consoleStream = console.newMessageStream();
        errorStream = new CapturingLiniesAndForwardingOutputStream(
                consoleStream,
                Charset.defaultCharset(),
                4,
                errorPrefixFilter);

        startTime = new Date();
    }

    @Override
    public void beginExecution() {
        // remove old output
        //console.clearConsole();
        consoleStream.println();

        // print info about command
        var purpose = command.getPurpose() != null ? format(": %s", command.getPurpose()) : "";
        consoleStream.println(ansi().a(ITALIC).a(startTime.toString()).a(purpose).reset().toString());
        consoleStream.println(ansi().a(INTENSITY_BOLD).a("Running Command:").reset().toString());
        consoleStream.println(
            " > " + commandLine.commandLineForDisplayPurposes()
                    .stream()
                    .map(this::simpleQuoteForDisplayOnly)
                    .collect(joining(" ")));
        consoleStream.println();
    }

    @Override
    public void executionCanceled() {
        consoleStream.println();
        consoleStream.print(ansi().fgBrightMagenta().a(INTENSITY_FAINT).a("Operation cancelled!").reset().toString());
    }

    @Override
    public void executionFailed(IOException cause) throws IOException {
        // close the error early
        errorStream.close();

        // extract last error output
        var stderrLinesFiltered = errorStream.getCapturedLinesFiltered()
                .stream()
                .map(s -> s.replaceAll("\u001B\\[[;\\d]*m", ""))
                .collect(joining(System.lineSeparator()));
        var stderrLines = errorStream.getCapturedLines()
                .stream()
                .map(s -> s.replaceAll("\u001B\\[[;\\d]*m", ""))
                .collect(joining(System.lineSeparator()));

        // throw enriched with error output if possible
        if (stderrLines.length() > 0) {
            throw new IOException(
                    format(
                        "%s%n---- Begin of Captured Error Output (last %d lines)----%n%s---- (more context) ----%n%s---- End of Captured Error Output ----%n",
                        cause.getMessage(),
                        errorStream.getLinesToCapture(),
                        stderrLinesFiltered,
                        stderrLines),
                    cause);
        }
        throw new IOException(format("%s%n(no error output was captured)", cause.getMessage()), cause);
    }

    @Override
    public void executionFinished(int exitCode) {
        var endTime = new Date();
        var executionTime =
                Duration.between(Instant.ofEpochMilli(startTime.getTime()), Instant.ofEpochMilli(endTime.getTime()));
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

        if (exitCode != 0) {
            consoleStream.println(ansi().fgBrightBlack().a("Process exited with ").a(exitCode).reset().toString());
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

    @Override
    public OutputStream getErrorStream() {
        return errorStream;
    }

    ImageDescriptor getImageDescriptoForConsole() {
        return BazelUIPlugin.getDefault().getImageRegistry().getDescriptor(BazelUIPlugin.ICON_BAZEL);
    }

    @Override
    public OutputStream getOutStream() {
        return consoleStream;
    }

    private void showConsole(MessageConsole console) {
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
    }

    private String simpleQuoteForDisplayOnly(String arg) {
        if (arg.indexOf(' ') > -1) {
            return (arg.indexOf('\'') > -1) ? '"' + arg + '"' : "'" + arg + "'";
        }
        return arg;
    }

}
