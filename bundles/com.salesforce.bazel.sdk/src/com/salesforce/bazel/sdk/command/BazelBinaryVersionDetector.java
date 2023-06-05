package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.readString;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.shell.ShellUtil;

/**
 * Invokes any Bazel binary command to detect it's version.
 */
public class BazelBinaryVersionDetector {

    private static final String BAZEL_VERSION_PREFIX = "bazel ";

    private final Path binary;
    private final boolean useShellEnvironment;
    private final ShellUtil shellUtil = new ShellUtil();

    public BazelBinaryVersionDetector(Path binary, boolean useShellEnvironment) {
        this.binary = binary;
        this.useShellEnvironment = useShellEnvironment;
    }

    /**
     * @return the detected Bazel version (never <code>null</code>)
     * @throws IOException
     *             if unable to detect the version
     * @throws InterruptedException
     */
    public BazelVersion detectVersion() throws IOException, InterruptedException {
        List<String> commandLine = List.of(binary.toString(), "--version");
        if (useShellEnvironment) {
            commandLine = shellUtil.wrapExecutionIntoShell(commandLine);
        }
        var pb = new ProcessBuilder(commandLine);
        var stdoutFile = File.createTempFile("bazel_version_", ".txt");
        pb.redirectOutput(stdoutFile);
        var stderrFile = File.createTempFile("bazel_version_", ".err.txt");
        pb.redirectError(stderrFile);
        var result = pb.start().waitFor();
        if (result != 0) {
            var out = readString(stderrFile.toPath(), Charset.defaultCharset());
            throw new IOException(format("Error executing '%s'. Process exited with code %d: %s",
                commandLine.stream().collect(joining(" ")), result, out));
        }
        var lines = readAllLines(stdoutFile.toPath(), Charset.defaultCharset());
        for (String potentialVersion : lines) {
            if (potentialVersion.startsWith(BAZEL_VERSION_PREFIX)) {
                return BazelVersion.parseVersion(potentialVersion.substring(BAZEL_VERSION_PREFIX.length()));
            }
        }

        var out = readString(stdoutFile.toPath(), Charset.defaultCharset());
        throw new IOException(format("No version information found in output of command '%s': %s",
            commandLine.stream().collect(joining(" ")), result, out));
    }
}
