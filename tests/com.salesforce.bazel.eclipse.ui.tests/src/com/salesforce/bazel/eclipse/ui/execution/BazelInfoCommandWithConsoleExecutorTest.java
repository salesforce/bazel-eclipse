package com.salesforce.bazel.eclipse.ui.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.jobs.Job;
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
    void default_bazel_version_detection() throws IOException {
        var command = new BazelInfoCommand(bazelWorkspace.getWorkspaceRoot());

        var result = executor.execute(command, () -> false);
        assertFalse(result.isEmpty());
    }

    @BeforeEach
    void setUp() throws Exception {
        var jobRef = new AtomicReference<Job>();

        executor = new EclipseConsoleBazelCommandExecutor() {
            @Override
            protected Job newInitJobFromPreferences() {
                var job = super.newInitJobFromPreferences();
                if (!jobRef.compareAndSet(null, job)) {
                    fail("Not expected to be called multiple times");
                }
                return job;
            }
        };

        // wait for init done
        jobRef.get().join();
    }

}
