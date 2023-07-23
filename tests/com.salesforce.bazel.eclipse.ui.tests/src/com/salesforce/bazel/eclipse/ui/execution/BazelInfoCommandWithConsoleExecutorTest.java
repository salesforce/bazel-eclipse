package com.salesforce.bazel.eclipse.ui.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.BazelInfoCommand;

import testdata.SharedTestData;
import testdata.utils.BazelWorkspaceExtension;

public class BazelInfoCommandWithConsoleExecutorTest {

    private static final BazelVersion BAZEL_VERSION = new BazelVersion(6, 1, 1);

    @RegisterExtension
    static BazelWorkspaceExtension bazelWorkspace =
            new BazelWorkspaceExtension(SharedTestData.WORKSPACE_001, SharedTestData.class, BAZEL_VERSION);

    private EclipseConsoleBazelCommandExecutor executor;

    @TempDir
    private Path tempDir;

    @Test
    void bazel_query_command() throws IOException {
        var command = new BazelQueryTestCommand(bazelWorkspace.getWorkspaceRoot(), "//...", true);

        var result = executor.execute(command, () -> false);
        assertFalse(result.isEmpty());
    }

    @Test
    void default_bazel_version_detection() throws IOException {
        var command = new BazelInfoCommand(bazelWorkspace.getWorkspaceRoot(), null);

        var result = executor.execute(command, () -> false);
        assertFalse(result.isEmpty());
    }

    @BeforeEach
    void setUp() throws Exception {
        executor = new EclipseConsoleBazelCommandExecutor();
    }

}
