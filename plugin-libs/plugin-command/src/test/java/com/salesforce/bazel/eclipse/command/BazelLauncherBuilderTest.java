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
package com.salesforce.bazel.eclipse.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.abstractions.BazelCommandArgs;
import com.salesforce.bazel.eclipse.command.mock.MockCommandBuilder.MockCommandSimulatedOutputMatcher;
import com.salesforce.bazel.eclipse.command.mock.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.TargetKind;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

public class BazelLauncherBuilderTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private static final int DEBUG_PORT = 1234;

    @Test
    public void testBuildRunCommand() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_BINARY;
        
        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyMap());
        
        addBazelCommandOutput(env, "run", "bazel run result");
        
        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals(env.bazelExecutable.getAbsolutePath(), cmdTokens.get(0));
        assertEquals("run", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.contains("debug"));
    }

    @Test
    public void testBuildRunCommandWithDebug() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_BINARY;
        
        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyMap());
        launcherBuilder.setDebugMode(true, "localhost", DEBUG_PORT);

        addBazelCommandOutput(env, "run", "bazel run result");

        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals(env.bazelExecutable.getAbsolutePath(), cmdTokens.get(0));
        assertEquals("run", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertTrue(cmdTokens.toString().contains(
            "-agentlib:jdwp=transport=dt_socket,address=localhost:" + DEBUG_PORT + ",server=y,suspend=y"));
    }

    @Test
    public void testBuildTestCommand() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_TEST;
        
        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyMap());

        addBazelCommandOutput(env, "test", "bazel test result");
        
        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals(env.bazelExecutable.getAbsolutePath(), cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.toString().contains("debug"));
    }

    @Test
    public void testBuildSeleniumTestCommand() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_WEB_TEST_SUITE;
        
        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyMap());

        addBazelCommandOutput(env, "test", "bazel test result");
        
        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals(env.bazelExecutable.getAbsolutePath(), cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.toString().contains("debug"));
    }
    
    @Test
    public void testBuildTestCommandWithFilter() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_TEST;
        Map<String, String> bazelArgs =
                Collections.singletonMap(BazelCommandArgs.TEST_FILTER.getName(), "someBazelTestFilter");

        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(bazelArgs);
        
        addBazelCommandOutput(env, "test", "bazel test result");

        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals(env.bazelExecutable.getAbsolutePath(), cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains("--test_filter=someBazelTestFilter"));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.toString().contains("debug"));
    }

    @Test
    public void testBuildTestCommandWithDebugEnabled() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_TEST;
        
        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyMap());
        launcherBuilder.setDebugMode(true, "localhost", DEBUG_PORT);
        
        addBazelCommandOutput(env, "test", "bazel test result");

        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals(env.bazelExecutable.getAbsolutePath(), cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertTrue(cmdTokens.toString().contains("debug"));
        assertTrue(cmdTokens.contains("--test_arg=--wrapper_script_flag=--debug=localhost:" + DEBUG_PORT));
    }
    
    // INTERNALS

    private TestBazelCommandEnvironmentFactory createEnv() throws Exception {
        File testDir = tmpFolder.newFolder();
        File workspaceDir = new File(testDir, "bazel-workspace");
        workspaceDir.mkdirs();
        File outputbaseDir = new File(testDir, "outputbase");
        outputbaseDir.mkdirs();
        TestBazelWorkspaceFactory workspace = new TestBazelWorkspaceFactory(workspaceDir, outputbaseDir).javaPackages(3).build();
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(workspace, testDir, null);
        
        return env;
    }
    
    private void addBazelCommandOutput(TestBazelCommandEnvironmentFactory env, String verb, String resultLine) {
        List<String> outputLines = new ArrayList<>();
        outputLines.add(resultLine);
        List<String> errorLines = new ArrayList<>();
        
        // create a matcher such that the resultLine is only returned if a command uses the specific verb
        List<MockCommandSimulatedOutputMatcher> matchers = new ArrayList<>();
        matchers.add(new MockCommandSimulatedOutputMatcher(0, verb));
        
        env.commandBuilder.addSimulatedOutput("launcherbuildertest", outputLines, errorLines, matchers);
    }

}
