/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
