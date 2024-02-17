package com.salesforce.bazel.eclipse.ui.execution;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.function.Predicate;

import org.eclipse.ui.console.MessageConsoleStream;

import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor.PreparedCommandLine;
import com.salesforce.bazel.sdk.command.ProcessStreamsProvider;
import com.salesforce.bazel.sdk.command.VerboseProcessStreamsProvider;

/**
 * A specialized {@link ProcessStreamsProvider} interacting with the Eclipse Console
 */
public class EclipseConsoleStreamsProvider extends VerboseProcessStreamsProvider {

    private static final Predicate<String> errorPrefixFilter = (var s) -> s.startsWith("ERROR:");

    private final MessageConsoleStream consoleStream;
    private final CapturingLiniesAndForwardingOutputStream errorStream;

    public EclipseConsoleStreamsProvider(BazelCommand<?> command, PreparedCommandLine commandLine) throws IOException {
        super(command, commandLine);

        var console = new BazelWorkspaceConsole(command.getWorkingDirectory());
        console.show();

        consoleStream = console.newMessageStream();
        errorStream = new CapturingLiniesAndForwardingOutputStream(
                consoleStream,
                Charset.defaultCharset(),
                4,
                errorPrefixFilter);
    }

    @Override
    public void close() throws IOException {
        consoleStream.close();
        errorStream.close();
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
    public OutputStream getErrorStream() {
        return errorStream;
    }

    @Override
    public OutputStream getOutStream() {
        return consoleStream;
    }

    @Override
    protected void print(String message) {
        consoleStream.print(message);
    }

    @Override
    protected void println() {
        consoleStream.println();
    }

    @Override
    protected void println(String line) {
        consoleStream.println(line);
    }

}
