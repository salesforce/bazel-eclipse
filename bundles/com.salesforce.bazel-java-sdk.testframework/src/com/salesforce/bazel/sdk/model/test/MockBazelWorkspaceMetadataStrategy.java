package com.salesforce.bazel.sdk.model.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceMetadataStrategy;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Mock impl for BazelWorkspaceMetadataStrategy suitable for tests. It mocks various metadata queries that we do against
 * Bazel workspaces.
 * <p>
 * Note that this implementation just returns in-memory File objects, it does not actually create the corresponding
 * dirs/files on the file system.
 */
public class MockBazelWorkspaceMetadataStrategy implements BazelWorkspaceMetadataStrategy {

    public String testWorkspaceName = null;
    public File workspaceRootDir = null;
    public File outputBaseDir = null;
    public OperatingEnvironmentDetectionStrategy os = null;

    // default paths are in sync with TestBazelWorkspaceFactory, which may be used to build a real test workspace on the file system
    // but override the below to change for a specific test

    public String execRootPath = null; // default: [outputbase]/execroot/test_workspace
    public String binPath = null; // default: [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/bin

    /**
     * The directories should be created already.
     *
     * @param workspaceRootDir
     */
    public MockBazelWorkspaceMetadataStrategy(String testWorkspaceName, File workspaceRootDir, File outputBaseDir,
            OperatingEnvironmentDetectionStrategy os) {
        this.testWorkspaceName = testWorkspaceName;
        this.workspaceRootDir = workspaceRootDir;
        if (!this.workspaceRootDir.exists()) {
            this.workspaceRootDir.mkdir();
            assertTrue(this.workspaceRootDir.exists());
        }
        this.outputBaseDir = outputBaseDir;
        if (!this.outputBaseDir.exists()) {
            this.outputBaseDir.mkdir();
            assertTrue(this.outputBaseDir.exists());
        }
        this.os = os;
    }

    @Override
    public File computeBazelWorkspaceExecRoot() {
        File execDir;

        if (execRootPath == null) {
            execRootPath = "execroot/" + testWorkspaceName;
        }
        execDir = new File(outputBaseDir, execRootPath);
        if (!execDir.exists()) {
            execDir.mkdirs();
            assertTrue(execDir.exists());
        }
        return execDir;
    }

    @Override
    public File computeBazelWorkspaceOutputBase() {
        assertTrue(outputBaseDir.exists());
        return outputBaseDir;
    }

    @Override
    public File computeBazelWorkspaceBin() {
        File binDir;

        if (binPath == null) {
            binPath = FSPathHelper.osSeps("execroot/" + testWorkspaceName + "/bazel-out/"
                    + os.getOperatingSystemDirectoryName(os.getOperatingSystemName()) + "-fastbuild/bin");
        }
        binDir = new File(outputBaseDir, binPath);
        if (!binDir.exists()) {
            binDir.mkdirs();
            assertTrue(binDir.exists());
        }
        return binDir;
    }

    private List<String> optionLines;

    public void mockCommandLineOptionOutput(List<String> optionLines) {
        this.optionLines = optionLines;
    }

    @Override
    public void populateBazelWorkspaceCommandOptions(BazelWorkspaceCommandOptions commandOptions) {
        if (optionLines == null) {
            optionLines = new ArrayList<>();
            optionLines.add("Inherited 'common' options: --isatty=1 --terminal_columns=260");
            optionLines.add(
                "Inherited 'build' options: --javacopt=-source 8 -target 8 --host_javabase=//tools/jdk:my-linux-jdk11 --javabase=//tools/jdk:my-linux-jdk8 --stamp");
        }
        commandOptions.parseOptionsFromOutput(optionLines);
    }

    @Override
    public List<String> computeBazelQuery(String query) {
        return null;
    }

}
