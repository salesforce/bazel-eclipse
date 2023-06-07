package com.salesforce.bazel.sdk.command;

import static java.nio.file.Files.readString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.salesforce.bazel.sdk.BazelVersion;

public class DefaultBazelCommandExecutorTest {

    static final BazelBinary bazelBinary = new BazelBinary(Path.of("echo"), new BazelVersion(999, 999, 999));

    private DefaultBazelCommandExecutor executor;

    @TempDir
    private Path tempDir;

    @Test
    void no_shell_wrapping_when_disabled() throws Exception {
        var command = new TestCommand();
        executor.configureBazelBinary(command);

        executor.setWrapExecutionIntoShell(false);
        var commandLine = executor.prepareCommandLine(command);
        assertEquals(List.of("echo", "dummy", "--tool_tag=java:sdk:test"),
            commandLine.fullCommandLineWithOptionalShellWrappingAndBinary());
    }

    @Test
    void redirect_to_file() throws Exception {
        var stdOutFile = tempDir.resolve("redirect.txt");

        var command = new TestCommand();
        command.setRedirectStdOutToFile(stdOutFile);

        var exitCode = executor.execute(command, () -> false);
        assertEquals(0, exitCode);

        var output = readString(stdOutFile, Charset.defaultCharset());
        assertNotNull(output);
        assertEquals("dummy --tool_tag=java:sdk:test" + System.lineSeparator(), output);
    }

    @BeforeEach
    void setup() {
        executor = new DefaultBazelCommandExecutor() {
            @Override
            protected String getToolTagArgument() {
                return "--tool_tag=java:sdk:test";
            }
        };
        executor.setBazelBinary(bazelBinary);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // shell wrapping is not supported on Windows
    void shell_wrapping_when_enabled() throws Exception {
        var command = new TestCommand();
        executor.configureBazelBinary(command);

        executor.setWrapExecutionIntoShell(true);
        var commandLine = executor.prepareCommandLine(command);

        // note we cannot test that this properly works
        // just check it's different
        assertNotEquals(List.of("echo", "dummy"), commandLine);
    }
}
