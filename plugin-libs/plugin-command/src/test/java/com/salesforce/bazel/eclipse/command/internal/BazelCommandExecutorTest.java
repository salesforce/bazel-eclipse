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
package com.salesforce.bazel.eclipse.command.internal;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.command.mock.MockWorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.mock.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

public class BazelCommandExecutorTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testHappy_StdOut() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();

        List<String> emptyLines = new ArrayList<>();
        List<String> outputLines = new ArrayList<>();
        outputLines.add("result line 1");
        outputLines.add("result line 2");
        env.commandBuilder.addSimulatedOutput("testcommand1", outputLines, emptyLines);
        
        List<String> args = new ArrayList<>();
        args.add("build");
        args.add("//projects/libs/javalib0");
        
        BazelCommandExecutor executor = new BazelCommandExecutor(env.bazelExecutable.bazelExecutableFile, env.commandBuilder);
        List<String> result = executor.runBazelAndGetOutputLines(env.bazelWorkspaceCommandRunner.getBazelWorkspaceRootDirectory(), 
            new MockWorkProgressMonitor(), args, (t) -> t);
        
        assertEquals(2, result.size());
        assertEquals("result line 1", result.get(0));
        assertEquals("result line 2", result.get(1));
    }

    @Test
    public void testHappy_StdErr() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();

        List<String> emptyLines = new ArrayList<>();
        List<String> errLines = new ArrayList<>();
        errLines.add("result line 1");
        errLines.add("result line 2");
        env.commandBuilder.addSimulatedOutput("testcommand1", emptyLines, errLines);
        
        List<String> args = new ArrayList<>();
        args.add("build");
        args.add("//projects/libs/javalib0");
        
        BazelCommandExecutor executor = new BazelCommandExecutor(env.bazelExecutable.bazelExecutableFile, env.commandBuilder);
        List<String> result = executor.runBazelAndGetErrorLines(env.bazelWorkspaceCommandRunner.getBazelWorkspaceRootDirectory(), 
            new MockWorkProgressMonitor(), args, (t) -> t, null, null);
        
        assertEquals(2, result.size());
        assertEquals("result line 1", result.get(0));
        assertEquals("result line 2", result.get(1));
    }
    
    @Test
    public void testStripInfo() {
        List<String> outputLines = new ArrayList<>();
        outputLines.add("result line 1");
        outputLines.add("result line 2");
        outputLines.add("INFO: result line 3");
        outputLines.add("result line 4");

        List<String> result = BazelCommandExecutor.stripInfoLines(outputLines);
        assertEquals(3, result.size());
        assertEquals("result line 1", result.get(0));
        assertEquals("result line 2", result.get(1));
        assertEquals("result line 4", result.get(2));
    }
    
    
    // INTERNAL
    
    private TestBazelCommandEnvironmentFactory createEnv() throws Exception {
        File testDir = tmpFolder.newFolder();
        File workspaceDir = new File(testDir, "bazel-workspace");
        workspaceDir.mkdirs();
        File outputbaseDir = new File(testDir, "outputbase");
        outputbaseDir.mkdirs();
        
        TestBazelWorkspaceDescriptor descriptor = new TestBazelWorkspaceDescriptor(workspaceDir, outputbaseDir).javaPackages(1);
        TestBazelWorkspaceFactory workspace = new TestBazelWorkspaceFactory(descriptor).build();
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(workspace, testDir, null);
        
        return env;
    }
}
