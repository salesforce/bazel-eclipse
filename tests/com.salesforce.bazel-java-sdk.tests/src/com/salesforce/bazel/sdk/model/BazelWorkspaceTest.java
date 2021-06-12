package com.salesforce.bazel.sdk.model;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.model.test.MockBazelWorkspaceMetadataStrategy;
import com.salesforce.bazel.sdk.model.test.MockOperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.util.BazelPathHelper;

public class BazelWorkspaceTest {

    @Test
    public void testWorkspaceBasicsMac() throws Exception {
        String wsName = "test-ws-basics";
        BazelWorkspace ws = createTestWorkspaceObject(wsName, "mac");

        // mostly just looking for NPE/runtime exceptions
        String wsDir = ws.getBazelWorkspaceRootDirectory().getAbsolutePath();
        assertTrue(wsDir.contains("bazel-java-sdk-" + wsName + "-workspace"));
        assertTrue(ws.getBazelOutputBaseDirectory().getAbsolutePath()
                .contains("bazel-java-sdk-" + wsName + "-outputdir"));
        assertTrue(ws.getBazelExecRootDirectory().getAbsolutePath().contains("execroot" + File.separatorChar + wsName));
        String binDir = BazelPathHelper.osSeps(ws.getBazelBinDirectory().getAbsolutePath()); // $SLASH_OK
        String binDirExpect = BazelPathHelper.osSeps("execroot/" + wsName + "/bazel-out/darwin-fastbuild/bin"); // $SLASH_OK
        assertTrue(binDir.contains(binDirExpect));
    }

    @Test
    public void testWorkspaceBasicsLinux() throws Exception {
        String wsName = "test-ws-basics";
        BazelWorkspace ws = createTestWorkspaceObject(wsName, "linux");

        // mostly just looking for NPE/runtime exceptions
        String wsDir = ws.getBazelWorkspaceRootDirectory().getAbsolutePath();
        assertTrue(wsDir.contains("bazel-java-sdk-" + wsName + "-workspace"));
        assertTrue(ws.getBazelOutputBaseDirectory().getAbsolutePath()
                .contains("bazel-java-sdk-" + wsName + "-outputdir"));
        assertTrue(ws.getBazelExecRootDirectory().getAbsolutePath().contains("execroot" + File.separatorChar + wsName));
        String binDir = BazelPathHelper.osSeps(ws.getBazelBinDirectory().getAbsolutePath()); // $SLASH_OK
        String binDirExpect = BazelPathHelper.osSeps("execroot/" + wsName + "/bazel-out/linux-fastbuild/bin"); // $SLASH_OK
        assertTrue(binDir.contains(binDirExpect));
    }

    @Test
    public void testWorkspaceBasicsWindows() throws Exception {
        String wsName = "test-ws-basics";
        BazelWorkspace ws = createTestWorkspaceObject(wsName, "win");

        // mostly just looking for NPE/runtime exceptions
        String wsDir = ws.getBazelWorkspaceRootDirectory().getAbsolutePath();
        assertTrue(wsDir.contains("bazel-java-sdk-" + wsName + "-workspace"));
        assertTrue(ws.getBazelOutputBaseDirectory().getAbsolutePath()
                .contains("bazel-java-sdk-" + wsName + "-outputdir"));
        assertTrue(ws.getBazelExecRootDirectory().getAbsolutePath().contains("execroot" + File.separator + wsName));
        String binDir = BazelPathHelper.osSeps(ws.getBazelBinDirectory().getAbsolutePath()); // $SLASH_OK
        String binDirExpect = BazelPathHelper.osSeps("execroot/" + wsName + "/bazel-out/windows-fastbuild/bin"); // $SLASH_OK
        assertTrue(binDir.contains(binDirExpect));
    }

    @Test
    public void testWorkspaceOptions() throws Exception {
        String wsName = "test-ws-basics";
        BazelWorkspace ws = createTestWorkspaceObject(wsName, "mac");

        BazelWorkspaceCommandOptions options = ws.getBazelWorkspaceCommandOptions();
        // we are just testing that the getBazelWorkspaceCommandOptions() method triggers a parse of the underlying metadata
        // we aren't testing the logic of the parsing itself
        assertTrue(options.getOption("stamp").equals("true")); // this is a default option in the MockBazelWorkspaceMetadataStrategy log lines
    }

    // HELPERS

    private BazelWorkspace createTestWorkspaceObject(String testName, String osName) throws Exception {
        File testBazelRoot = File.createTempFile("bazel-java-sdk-" + testName + "-workspace", "");
        File testBazelOutput = File.createTempFile("bazel-java-sdk-" + testName + "-outputdir", "");
        MockOperatingEnvironmentDetectionStrategy os = new MockOperatingEnvironmentDetectionStrategy(osName);

        // this mock simulates .bazelrc options
        MockBazelWorkspaceMetadataStrategy metadata =
                new MockBazelWorkspaceMetadataStrategy(testName, testBazelRoot, testBazelOutput, os);

        return new BazelWorkspace(testName, testBazelRoot, os, metadata);
    }
}
