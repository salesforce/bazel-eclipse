package com.salesforce.bazel.eclipse.ui.execution;

import static java.lang.String.format;
import static java.nio.file.Files.newOutputStream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_BOLD;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_FAINT;
import static org.fusesource.jansi.Ansi.Attribute.ITALIC;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.concurrent.TimeUnit;

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

    private static Logger LOG = LoggerFactory.getLogger(EclipseConsoleBazelCommandExecutor.class);

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
    protected <R> R doExecuteProcess(BazelCommand<R> command, CancelationCallback cancelationCallback,
            ProcessBuilder processBuilder) throws IOException {

        var console = findConsole(format("Bazel Workspace (%s)", command.getWorkingDirectory()));
        showConsole(console);

        try (final var consoleStream = console.newMessageStream();
                final var stdout =
                        command.getStdOutFile() != null ? newOutputStream(command.getStdOutFile()) : consoleStream) {
            // remove old output
            console.clearConsole();

            // print info about command
            consoleStream.println(ansi().a(ITALIC).a(new Date().toString()).reset().toString());
            consoleStream.println(ansi().a(INTENSITY_BOLD).a("Running Command:").reset().toString());
            consoleStream.println(" > bazel " + command.prepareCommandLine(requireNonNull(command.getBazelBinary(),
                "command is expected to have a binary at this point; check the code flow").bazelVersion()));
            consoleStream.println();

            var fullCommandLine = processBuilder.command().stream().collect(joining(" "));
            LOG.debug(fullCommandLine);

            int result;
            try {
                final var process = processBuilder.start();

                final var p1 = pipe(process.getInputStream(), stdout, fullCommandLine);
                final var p2 = pipe(process.getErrorStream(), consoleStream, fullCommandLine);

                try {
                    while (!process.waitFor(500L, TimeUnit.MILLISECONDS)) {
                        if (cancelationCallback.isCanceled()) {
                            process.destroyForcibly();
                            consoleStream.println();
                            consoleStream.print(ansi().fgBrightMagenta().a(INTENSITY_FAINT).a("Operation cancelled!")
                                    .reset().toString());
                            throw new OperationCanceledException("user cancelled");
                        }
                    }
                } finally {
                    // interrupt pipe threads so they'll die
                    p1.interrupt();
                    p2.interrupt();
                }

                result = process.exitValue();

                if (result != 0) {
                    consoleStream.println();
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
            return command.generateResult(result);
        }
    }

    private void showConsole(MessageConsole console) {
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
    }
}
