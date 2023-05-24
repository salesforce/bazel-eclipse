package com.salesforce.bazel.sdk.command.shell;

import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.readString;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class MacOsLoginShellFinder {

    private static final String USER_SHELL_PREFIX = "UserShell: ";

    public Path detectLoginShell() throws IOException {
        return detectLoginShell(System.getProperty("user.name"));
    }

    Path detectLoginShell(String user) throws IOException {
        var processBuilder = new ProcessBuilder("dscl", ".", "-read", "/Users/" + user, "UserShell");

        var output = File.createTempFile("dscl", "shell");
        processBuilder.redirectOutput(output);
        processBuilder.redirectErrorStream();

        var process = processBuilder.start();
        try {
            process.waitFor(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IOException("Unable to detect shell: timeout waiting for result.", e);
        }

        if (process.exitValue() != 0) {
            throw new IOException(format("Unable to detect shell: process exit with %d%n%n%s%n---", process.exitValue(),
                readString(output.toPath())));
        }

        var lines = readAllLines(output.toPath());
        if ((lines.size() != 1) || !lines.get(0).startsWith(USER_SHELL_PREFIX)) {
            throw new IOException(
                    "Unable to detect shell: unexpected result: " + lines.stream().collect(joining("\n", "\n", "")));
        }

        return Path.of(lines.get(0).substring(USER_SHELL_PREFIX.length()));
    }
}
