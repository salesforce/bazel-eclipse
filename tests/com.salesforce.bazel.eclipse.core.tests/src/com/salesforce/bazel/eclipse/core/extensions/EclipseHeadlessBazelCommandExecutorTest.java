package com.salesforce.bazel.eclipse.core.extensions;

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

public class EclipseHeadlessBazelCommandExecutorTest {

    static final BazelBinary bazelBinary = new BazelBinary(Path.of("echo"), new BazelVersion(999, 999, 999));

    static EclipseHeadlessBazelCommandExecutor newExecutorWithStaticBinary(BazelBinary binary) {
        return new EclipseHeadlessBazelCommandExecutor() {
            @Override
            protected void initializeBazelBinary() {
                setBazelBinary(binary);
            }
        };
    }

    private EclipseHeadlessBazelCommandExecutor executor;

    @TempDir
    private Path tempDir;

    @Test
    void redirect_to_stdout() throws IOException {
        var stdOutFile = tempDir.resolve("redirect.txt");

        var command = new TestCommand();
        command.setRedirectStdOutToFile(stdOutFile);

        executor.setWrapExecutionIntoShell(true);
        var exitCode = executor.execute(command, () -> false);
        assertEquals(0, exitCode);

        var output = readString(stdOutFile, Charset.defaultCharset());
        assertNotNull(output);
        assertEquals("dummy" + System.lineSeparator(), output);
    }

    @BeforeEach
    void setUp() throws Exception {
        executor = newExecutorWithStaticBinary(bazelBinary);
    }

}
