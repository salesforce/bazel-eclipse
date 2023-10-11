package com.salesforce.bazel.sdk.command.shell;

import static java.lang.String.format;
import static java.nio.file.Files.isExecutable;
import static java.nio.file.Files.readAllLines;

import java.io.IOException;
import java.nio.file.Path;

public class UnixLoginShellFinder {

    public Path detectLoginShell() throws IOException {
        return detectLoginShell(System.getProperty("user.name"));
    }

    Path detectLoginShell(String username) throws IOException {
        // prefer environment variable
        var shell = EnvironmentUtil.getShell();
        if (shell != null) {
            var shellBinary = Path.of(shell);
            if (!isExecutable(shellBinary)) {
                throw new IOException(shellBinary + " is not executable!");
            }
            return shellBinary;
        }
        var lines = readAllLines(Path.of("/etc/passwd"));
        for (String line : lines) {
            // /etc/passwd in Linux (split by ':', 7 fields, login shell is last)
            var tokens = line.split(":");
            if (tokens.length < 7) {
                throw new IOException("Unparsable /etc/passwd line: " + line);
            }
            if (tokens[0].equals(username)) {
                return Path.of(tokens[6]);
            }
        }
        throw new IOException(format("Unable to find /etc/passwd entry for user '%s'", username));
    }

}