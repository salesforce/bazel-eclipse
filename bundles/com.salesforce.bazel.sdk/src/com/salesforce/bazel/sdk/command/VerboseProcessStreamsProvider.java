package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_BOLD;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_FAINT;
import static org.fusesource.jansi.Ansi.Attribute.ITALIC;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor.PreparedCommandLine;

/**
 * A version of {@link ProcessStreamsProvider} printing more information about the command execution.
 */
public abstract class VerboseProcessStreamsProvider extends ProcessStreamsProvider {

    protected static String humanReadableFormat(Duration duration) {
        return duration.truncatedTo(ChronoUnit.MILLIS)
                .toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }

    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE HH:mm:ss");

    protected final BazelCommand<?> command;
    protected final PreparedCommandLine commandLine;
    private final Instant startInstant;

    private Instant endInstant;

    public VerboseProcessStreamsProvider(BazelCommand<?> command, PreparedCommandLine commandLine) {
        this.command = command;
        this.commandLine = commandLine;
        startInstant = Instant.now();
    }

    @Override
    public void beginExecution() {
        println();

        // print info about command
        var purpose = command.getPurpose() != null ? format(": %s", command.getPurpose()) : "";
        println(
            ansi().a(ITALIC)
                    .a(dateFormat.format(LocalDateTime.ofInstant(startInstant, ZoneId.systemDefault())))
                    .a(INTENSITY_BOLD)
                    .a(purpose)
                    .reset()
                    .toString());
        println(
            ansi().fgBlue()
                    .a(
                        "> " + commandLine.commandLineForDisplayPurposes()
                                .stream()
                                .map(this::simpleQuoteForDisplayOnly)
                                .collect(joining(" ")))
                    .reset()
                    .toString());
        println();
    }

    @Override
    public void executionCanceled() {
        println();
        println(ansi().fgBrightMagenta().a(INTENSITY_FAINT).a("Operation cancelled!").reset().toString());
    }

    @Override
    public void executionFinished(int exitCode) {
        endInstant = Instant.now();
        var executionTime = Duration.between(startInstant, endInstant);
        println();
        println(
            ansi().a(ITALIC)
                    .a("Process finished in ")
                    .a(INTENSITY_BOLD)
                    .a(humanReadableFormat(executionTime))
                    .reset()
                    .toString());

        if (exitCode != 0) {
            println(ansi().fgBrightBlack().a("Process exited with ").a(exitCode).reset().toString());
        }

        println();
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
