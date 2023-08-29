package com.salesforce.bazel.eclipse.ui.execution;

import static java.nio.file.Files.readString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.BazelBinary;

public class EclipseConsoleBazelCommandExecutorTest {

    static final BazelBinary bazelBinary = new BazelBinary(Path.of("echo"), new BazelVersion(999, 999, 999));

    static EclipseConsoleBazelCommandExecutor newExecutorWithStaticBinary(BazelBinary binary) {
        return new EclipseConsoleBazelCommandExecutor() {
            @Override
            protected String getToolTagArgument() {
                return "--tool_tag=eclipse:test";
            }

            @Override
            protected void initializeBazelBinary() {
                setBazelBinary(binary);
            }
        };
    }

    private EclipseConsoleBazelCommandExecutor executor;

    @TempDir
    private Path tempDir;

    @Test
    void redirect_to_stdout() throws IOException {
        var stdOutFile = tempDir.resolve("redirect.txt");

        var command = new TestCommand();
        command.setRedirectStdOutToFile(stdOutFile);

        var exitCode = executor.execute(command, () -> false);
        assertEquals(0, exitCode);

        var output = readString(stdOutFile, Charset.defaultCharset());
        assertNotNull(output);
        assertEquals(
            "dummy --tool_tag=eclipse:test --color=yes --curses=no --progress_in_terminal_title=no"
                    + System.lineSeparator(),
            output);
    }

    @BeforeEach
    void setUp() throws Exception {
        executor = newExecutorWithStaticBinary(bazelBinary);
    }

}
