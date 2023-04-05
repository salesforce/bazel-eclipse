package com.salesforce.bazel.sdk.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.salesforce.bazel.sdk.BazelVersion;

public class DefaultBazelCommandExecutorTest {

    static final BazelBinary bazelBinary = new BazelBinary(Path.of("bazel"), new BazelVersion(999, 999, 999));
    private DefaultBazelCommandExecutor executor;

    @Test
    void no_shell_wrapping_when_disabled() throws Exception {
        executor.setWrapExecutionIntoShell(false);
        var commandLine = executor.prepareCommandLine(new DummyCommand());
        assertEquals(List.of("bazel", "dummy"), commandLine);
    }

    @BeforeEach
    void setup() {
        executor = new DefaultBazelCommandExecutor();
        executor.setBazelBinary(bazelBinary);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // shell wrapping is not supported on Windows
    void shell_wrapping_when_enabled() throws Exception {
        executor.setWrapExecutionIntoShell(true);
        var commandLine = executor.prepareCommandLine(new DummyCommand());

        // note we cannot test that this properly works
        // just check it's different
        assertNotEquals(List.of("bazel", "dummy"), commandLine);
    }
}
