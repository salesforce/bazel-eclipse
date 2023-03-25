package com.salesforce.bazel.sdk.model;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.model.test.MockBazelWorkspaceMetadataStrategy;
import com.salesforce.bazel.sdk.model.test.MockOperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.path.FSPathHelper;

public class BazelWorkspaceTest {

    private BazelWorkspace createTestWorkspaceObject(String testName, String osName) throws Exception {
        var testDir = Files.createTempDirectory("bzl-mvninstall-test-");
        var workspaceDir = new File(testDir.toFile(), "bazel-java-sdk-" + testName + "-workspace");
        var outputDir = new File(testDir.toFile(), "bazel-java-sdk-" + testName + "-outputdir");

        MockOperatingEnvironmentDetectionStrategy os = new MockOperatingEnvironmentDetectionStrategy(osName);

        // this mock simulates .bazelrc options
        MockBazelWorkspaceMetadataStrategy metadata =
                new MockBazelWorkspaceMetadataStrategy(testName, workspaceDir, outputDir, os);

        return new BazelWorkspace(testName, workspaceDir, os, metadata);
    }

    @Test
    public void testWorkspaceBasicsLinux() throws Exception {
        var wsName = "test-ws-basics";
        var ws = createTestWorkspaceObject(wsName, "linux");

        // mostly just looking for NPE/runtime exceptions
        var wsDir = ws.getBazelWorkspaceRootDirectory().getAbsolutePath();
        assertTrue(wsDir.contains("bazel-java-sdk-" + wsName + "-workspace"));
        assertTrue(
            ws.getBazelOutputBaseDirectory().getAbsolutePath().contains("bazel-java-sdk-" + wsName + "-outputdir"));
        assertTrue(ws.getBazelExecRootDirectory().getAbsolutePath().contains("execroot" + File.separatorChar + wsName));
        var binDir = FSPathHelper.osSeps(ws.getBazelBinDirectory().getAbsolutePath()); // $SLASH_OK
        var binDirExpect = FSPathHelper.osSeps("execroot/" + wsName + "/bazel-out/linux-fastbuild/bin"); // $SLASH_OK
        assertTrue(binDir.contains(binDirExpect));
    }

    @Test
    public void testWorkspaceBasicsMac() throws Exception {
        var wsName = "test-ws-basics";
        var ws = createTestWorkspaceObject(wsName, "mac");

        // mostly just looking for NPE/runtime exceptions
        var wsDir = ws.getBazelWorkspaceRootDirectory().getAbsolutePath();
        assertTrue(wsDir.contains("bazel-java-sdk-" + wsName + "-workspace"));
        assertTrue(
            ws.getBazelOutputBaseDirectory().getAbsolutePath().contains("bazel-java-sdk-" + wsName + "-outputdir"));
        assertTrue(ws.getBazelExecRootDirectory().getAbsolutePath().contains("execroot" + File.separatorChar + wsName));
        var binDir = FSPathHelper.osSeps(ws.getBazelBinDirectory().getAbsolutePath()); // $SLASH_OK
        var binDirExpect = FSPathHelper.osSeps("execroot/" + wsName + "/bazel-out/darwin-fastbuild/bin"); // $SLASH_OK
        assertTrue(binDir.contains(binDirExpect));
    }

    @Test
    public void testWorkspaceBasicsWindows() throws Exception {
        var wsName = "test-ws-basics";
        var ws = createTestWorkspaceObject(wsName, "win");

        // mostly just looking for NPE/runtime exceptions
        var wsDir = ws.getBazelWorkspaceRootDirectory().getAbsolutePath();
        assertTrue(wsDir.contains("bazel-java-sdk-" + wsName + "-workspace"));
        assertTrue(
            ws.getBazelOutputBaseDirectory().getAbsolutePath().contains("bazel-java-sdk-" + wsName + "-outputdir"));
        assertTrue(ws.getBazelExecRootDirectory().getAbsolutePath().contains("execroot" + File.separator + wsName));
        var binDir = FSPathHelper.osSeps(ws.getBazelBinDirectory().getAbsolutePath()); // $SLASH_OK
        var binDirExpect = FSPathHelper.osSeps("execroot/" + wsName + "/bazel-out/windows-fastbuild/bin"); // $SLASH_OK
        assertTrue(binDir.contains(binDirExpect));
    }

    // HELPERS

    @Test
    public void testWorkspaceOptions() throws Exception {
        var wsName = "test-ws-basics";
        var ws = createTestWorkspaceObject(wsName, "mac");

        var options = ws.getBazelWorkspaceCommandOptions();
        // we are just testing that the getBazelWorkspaceCommandOptions() method triggers a parse of the underlying metadata
        // we aren't testing the logic of the parsing itself
        assertTrue("true".equals(options.getOption("stamp"))); // this is a default option in the MockBazelWorkspaceMetadataStrategy log lines
    }
}
