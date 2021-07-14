/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.command.test;

import java.io.File;

import org.mockito.Mockito;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Factory for creating test environments for Bazel Command functional tests. Produces a real
 * BazelWorkspaceCommandRunner with collaborators, with mock command execution underneath it all. Logically, this layer
 * replaces the real Bazel executable with command simulations.
 */
public class TestBazelCommandEnvironmentFactory {
    public TestBazelWorkspaceFactory testWorkspace;
    public BazelWorkspaceCommandRunner globalCommandRunner;
    public BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner;

    public MockBazelAspectLocation bazelAspectLocation;
    public MockCommandConsole commandConsole;
    public MockCommandBuilder commandBuilder;
    public MockBazelExecutable bazelExecutable;

    /**
     * Basic testing environment for the command layer. It creates a simple Bazel workspace on the filesystem with no
     * Java/generic packages. This is only useful for very basic tests that don't need a Bazel workspace.
     */
    public void createTestEnvironment(File tempDir, TestOptions testOptions) throws Exception {
        // the name of the directory that contains the bazel workspace is significant, as the Eclipse feature
        // will use it in the name of the Eclipse project
        File workspaceDir = new File(tempDir, "bazel-workspace");
        workspaceDir.mkdirs();
        File outputBase = new File(tempDir, "outputbase");
        outputBase.mkdirs();

        if (testOptions == null) {
            testOptions = new TestOptions();
        }

        TestBazelWorkspaceDescriptor descriptor =
                new TestBazelWorkspaceDescriptor(workspaceDir, outputBase, "bazel_command_executor_test");
        TestBazelWorkspaceFactory testWorkspace = new TestBazelWorkspaceFactory(descriptor);
        testWorkspace.build();
        createTestEnvironment(testWorkspace, tempDir, testOptions);
    }

    /**
     * Creates a testing environment based on a test workspace passed in from the caller. If you are testing commands in
     * the context of actual Bazel packages (e.g. Java) this is the form to use.
     */
    public void createTestEnvironment(TestBazelWorkspaceFactory testWorkspace, File tempDir, TestOptions testOptions) {
        this.testWorkspace = testWorkspace;

        File execDir = new File(tempDir, "executable");
        execDir.mkdir();
        bazelExecutable = new MockBazelExecutable(execDir);

        if (testOptions == null) {
            testOptions = new TestOptions();
        }

        bazelAspectLocation = new MockBazelAspectLocation(tempDir, "test-aspect-label");
        commandConsole = new MockCommandConsole();
        commandBuilder = new MockCommandBuilder(commandConsole, testWorkspace, testOptions);

        BazelCommandManager bazelCommandManager = new BazelCommandManager(bazelAspectLocation, commandBuilder,
                commandConsole, bazelExecutable.bazelExecutableFile);
        bazelCommandManager.setBazelExecutablePath(bazelExecutable.bazelExecutableFile.getAbsolutePath());

        OperatingEnvironmentDetectionStrategy osStrategy = Mockito.mock(OperatingEnvironmentDetectionStrategy.class);
        BazelWorkspace bazelWorkspace =
                new BazelWorkspace("test", testWorkspace.workspaceDescriptor.workspaceRootDirectory, osStrategy);
        bazelWorkspace.setBazelWorkspaceMetadataStrategy(bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace));

        globalCommandRunner = bazelCommandManager.getGlobalCommandRunner();
        bazelWorkspaceCommandRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
    }

}
