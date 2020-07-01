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
package com.salesforce.bazel.sdk.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.command.BazelCommandArgs;
import com.salesforce.bazel.sdk.command.BazelLauncherBuilder;
import com.salesforce.bazel.sdk.command.test.MockCommandSimulatedOutputMatcher;
import com.salesforce.bazel.sdk.command.test.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.TargetKind;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;

public class BazelLauncherBuilderTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private static final int DEBUG_PORT = 1234;

    @Test
    public void testBuildRunCommand() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0");
        TargetKind targetKind = TargetKind.JAVA_BINARY;

        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyList());

        addBazelCommandOutput(env, 0, "bazel-bin/projects/libs/javalib0/javalib0", "fake bazel launcher script result");

        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();
        assertEquals("bazel-bin/projects/libs/javalib0/javalib0", cmdTokens.get(0));
        assertFalse(cmdTokens.contains("debug"));
    }

    @Test
    public void testBuildRunCommandWithDebug() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0");
        TargetKind targetKind = TargetKind.JAVA_BINARY;

        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyList());
        launcherBuilder.setDebugMode(true, "localhost", DEBUG_PORT);

        addBazelCommandOutput(env, 0, "bazel-bin/projects/libs/javalib0/javalib0", "fake bazel launcher script result");

        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals("bazel-bin/projects/libs/javalib0/javalib0", cmdTokens.get(0));
        assertFalse(cmdTokens.contains("debug=" + DEBUG_PORT));
    }

    @Test
    public void testBuildTestCommand() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0");
        TargetKind targetKind = TargetKind.JAVA_TEST;

        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyList());

        addBazelCommandOutput(env, 1, "test", "bazel test result");

        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals(env.bazelExecutable.getAbsolutePath(), cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.toString().contains("debug"));
    }

    @Test
    public void testBuildSeleniumTestCommand() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0");
        TargetKind targetKind = TargetKind.JAVA_WEB_TEST_SUITE;

        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyList());

        addBazelCommandOutput(env, 1, "test", "bazel test result");

        List<String> cmdTokens = launcherBuilder.build().getProcessBuilder().command();

        assertEquals(env.bazelExecutable.getAbsolutePath(), cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.toString().contains("debug"));
    }

    @Test
    public void testBuildTestCommandWithFilter() throws Exception {
        TestBazelCommandEnvironmentFactory env = createEnv();
        BazelLabel label = new BazelLabel("//projects/libs/javalib0");
        TargetKind targetKind = TargetKind.JAVA_TEST;
        List<String> bazelArgs =
                Collections.singletonList(BazelCommandArgs.TEST_FILTER.getName() + "=someBazelTestFilter");

        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(bazelArgs);

        addBazelCommandOutput(env, 1, "test", "bazel test result");

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
        BazelLabel label = new BazelLabel("//projects/libs/javalib0");
        TargetKind targetKind = TargetKind.JAVA_TEST;

        BazelLauncherBuilder launcherBuilder = env.bazelWorkspaceCommandRunner.getBazelLauncherBuilder();
        launcherBuilder.setLabel(label);
        launcherBuilder.setTargetKind(targetKind);
        launcherBuilder.setArgs(Collections.emptyList());
        launcherBuilder.setDebugMode(true, "localhost", DEBUG_PORT);

        addBazelCommandOutput(env, 1, "test", "bazel test result");

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
        TestBazelWorkspaceDescriptor descriptor = new TestBazelWorkspaceDescriptor(workspaceDir, outputbaseDir).javaPackages(3);
        TestBazelWorkspaceFactory workspace = new TestBazelWorkspaceFactory(descriptor).build();
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(workspace, testDir, null);

        return env;
    }

    private void addBazelCommandOutput(TestBazelCommandEnvironmentFactory env, int verbIndex, String verb, String resultLine) {
        List<String> outputLines = new ArrayList<>();
        outputLines.add(resultLine);
        List<String> errorLines = new ArrayList<>();

        // create a matcher such that the resultLine is only returned if a command uses the specific verb
        List<MockCommandSimulatedOutputMatcher> matchers = new ArrayList<>();
        matchers.add(new MockCommandSimulatedOutputMatcher(verbIndex, verb));

        env.commandBuilder.addSimulatedOutput("launcherbuildertest", outputLines, errorLines, matchers);
    }

}
