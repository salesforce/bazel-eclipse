package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_BOLD;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_FAINT;
import static org.fusesource.jansi.Ansi.Attribute.ITALIC;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor.PreparedCommandLine;

/**
 * A version of {@link ProcessStreamsProvider} printing more information about the command execution.
 */
public abstract class VerboseProcessStreamsProvider extends ProcessStreamsProvider {

    protected static String humanReadableFormat(Duration duration) {
        return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
    }

    protected final BazelCommand<?> command;
    protected final PreparedCommandLine commandLine;

    private final Date startTime;

    public VerboseProcessStreamsProvider(BazelCommand<?> command, PreparedCommandLine commandLine) {
        this.command = command;
        this.commandLine = commandLine;
        startTime = new Date();

    }

    @Override
    public void beginExecution() {
        // remove old output
        //console.clearConsole();
        println();

        // print info about command
        var purpose = command.getPurpose() != null ? format(": %s", command.getPurpose()) : "";
        println(ansi().a(ITALIC).a(startTime.toString()).a(purpose).reset().toString());
        println(ansi().a(INTENSITY_BOLD).a("Running Command:").reset().toString());
        println(
            " > " + commandLine.commandLineForDisplayPurposes()
                    .stream()
                    .map(this::simpleQuoteForDisplayOnly)
                    .collect(joining(" ")));
        println();
    }

    @Override
    public void executionCanceled() {
        println();
        println(ansi().fgBrightMagenta().a(INTENSITY_FAINT).a("Operation cancelled!").reset().toString());
    }

    @Override
    public void executionFinished(int exitCode) {
        var endTime = new Date();
        var executionTime =
                Duration.between(Instant.ofEpochMilli(startTime.getTime()), Instant.ofEpochMilli(endTime.getTime()));
        println();
        println(
            ansi().a(ITALIC)
                    .a("Process finished in ")
                    .a(humanReadableFormat(executionTime))
                    .a(" (at ")
                    .a(endTime.toString())
                    .a(")")
                    .reset()
                    .toString());

        if (exitCode != 0) {
            println(ansi().fgBrightBlack().a("Process exited with ").a(exitCode).reset().toString());
        }
    }

    /**
     * Appends the system newline to {@link #getOutStream()}.
     */
    protected abstract void println();

    /**
     * Appends the specified message to {@link #getOutStream()}, followed by a line separator string.
     *
     * @param message
     *            message to print
     */
    protected abstract void println(String message);

    private String simpleQuoteForDisplayOnly(String arg) {
        if (arg.indexOf(' ') > -1) {
            return (arg.indexOf('\'') > -1) ? '"' + arg + '"' : "'" + arg + "'";
        }
        return arg;
    }
}
