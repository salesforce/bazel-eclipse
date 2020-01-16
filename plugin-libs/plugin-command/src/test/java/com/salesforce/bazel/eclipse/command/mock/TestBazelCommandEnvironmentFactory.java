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
package com.salesforce.bazel.eclipse.command.mock;

import java.io.File;

import org.mockito.Mockito;

import com.salesforce.bazel.eclipse.command.BazelCommandManager;
import com.salesforce.bazel.eclipse.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.eclipse.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.model.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

/**
 * Factory for creating test environments for Bazel Command functional tests. Produces a real BazelWorkspaceCommandRunner with 
 * collaborators, with mock command execution underneath it all. Logically, this layer replaces the real Bazel executable with
 * command simulations.
 * 
 * @author plaird
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
     * Basic testing environment for the command layer. It creates a simple Bazel workspace on the filesystem
     * with no Java/generic packages.
     */
    public void createTestEnvironment(File tempDir) throws Exception {
        // the name of the directory that contains the bazel workspace is significant, as the Eclipse feature
        // will use it in the name of the Eclipse project
        File workspaceDir = new File(tempDir, "bazel-workspace");
        workspaceDir.mkdirs();
        File outputBase = new File(tempDir, "outputbase");
        outputBase.mkdirs();
        
        TestBazelWorkspaceFactory testWorkspace = new TestBazelWorkspaceFactory(workspaceDir, outputBase, "bazel_command_executor_test");
        testWorkspace.build();
        createTestEnvironment(testWorkspace, tempDir);
    }
    
    /**
     * Creates a testing environment based on a test workspace passed in from the caller. If you are testing
     * commands in the context of actual Bazel packages (e.g. Java) this is the form to use.
     */
    public void createTestEnvironment(TestBazelWorkspaceFactory testWorkspace, File tempDir) {
        this.testWorkspace = testWorkspace;
        
        File execDir = new File(tempDir, "executable");
        execDir.mkdir();
        this.bazelExecutable = new MockBazelExecutable(execDir);
        
        this.bazelAspectLocation = new MockBazelAspectLocation(tempDir, "test-aspect-label");
        this.commandConsole = new MockCommandConsole();

        this.commandBuilder = new MockCommandBuilder(commandConsole, testWorkspace.dirWorkspaceRoot, testWorkspace.dirOutputBase, 
            testWorkspace.dirExecRoot, testWorkspace.dirBazelBin);
        // when the workspace factory built out the Bazel workspace file system, it wrote a collection of aspect json files
        // we need to tell the MockCommandBuilder where they are, since it will need to return them in command results
        this.commandBuilder.addAspectJsonFileResponses(this.testWorkspace.aspectFileSets);

        BazelCommandManager bazelCommandManager = new BazelCommandManager(bazelAspectLocation, commandBuilder, commandConsole, 
            bazelExecutable.bazelExecutableFile);
        bazelCommandManager.setBazelExecutablePath(bazelExecutable.bazelExecutableFile.getAbsolutePath());
        
        BazelWorkspace bazelWorkspace = new BazelWorkspace(testWorkspace.dirWorkspaceRoot, Mockito.mock(OperatingEnvironmentDetectionStrategy.class));
        this.globalCommandRunner = bazelCommandManager.getGlobalCommandRunner();
        this.bazelWorkspaceCommandRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
    }
    
}
