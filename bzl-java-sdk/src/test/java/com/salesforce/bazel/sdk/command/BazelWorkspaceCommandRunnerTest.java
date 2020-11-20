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

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.command.test.MockWorkProgressMonitor;
import com.salesforce.bazel.sdk.command.test.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.sdk.command.test.type.MockVersionCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

public class BazelWorkspaceCommandRunnerTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    // GLOBAL COMMAND RUNNER TESTS
    // The global runner is for commands that aren't associated with a particular workspace, typically
    // executed by an IDE prior to import of a workspace.

    @Test
    public void testGlobalRunner_bazelpath() throws Exception {
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(tmpFolder.newFolder(), null);

        // verify that the command runner has the Bazel exec path
        assertEquals(env.bazelExecutable.getAbsolutePath(), BazelWorkspaceCommandRunner.getBazelExecutablePath());
    }

    @Test
    public void testGlobalRunner_checkBazelVersion() throws Exception {
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(tmpFolder.newFolder(), null);

        // run our version check, will throw if version is not approved
        // during tests, the Bazel command is simulated by MockVersionCommand
        BazelWorkspaceCommandRunner globalRunner = env.globalCommandRunner;
        globalRunner.runBazelVersionCheck();
    }

    @Test(expected = BazelCommandLineToolConfigurationException.class)
    public void testGlobalRunner_checkBazelVersion_fail() throws Exception {
        TestOptions testOptions = new TestOptions();
        // minimum supported Bazel version is currently 1.0.0, so this should cause check to fail
        testOptions.put(MockVersionCommand.TESTOPTION_BAZELVERSION, "0.9.0");

        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(tmpFolder.newFolder(), testOptions);

        // run our version check, will throw if version is not approved
        // during tests, the Bazel command is simulated by MockVersionCommand
        BazelWorkspaceCommandRunner globalRunner = env.globalCommandRunner;
        globalRunner.runBazelVersionCheck();
    }

    // WORKSPACE COMMAND RUNNER BASIC TESTS
    // This set mostly tests our mocking framework to make sure everything is wired up correctly
    // and workspace specific commands are successful.

    @Test
    public void testWorkspaceRunner_workspacesetup() throws Exception {
        File testDir = tmpFolder.newFolder();
        File workspaceDir = new File(testDir, "bazel-workspace");
        workspaceDir.mkdirs();
        File outputbaseDir = new File(testDir, "outputbase");
        outputbaseDir.mkdirs();

        // setup a test workspace on disk, this will write out WORKSPACE, BUILD and aspect files
        TestBazelWorkspaceDescriptor descriptor =
                new TestBazelWorkspaceDescriptor(workspaceDir, outputbaseDir).javaPackages(3);
        TestBazelWorkspaceFactory workspace = new TestBazelWorkspaceFactory(descriptor).build();
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(workspace, testDir, null);

        // get the command runner associated with our test workspace on disk
        BazelWorkspaceCommandRunner workspaceRunner = env.bazelWorkspaceCommandRunner;

        // test the setup, for example we are loading the workspace aspects from the file system
        String label = "//projects/libs/javalib0:*";
        Set<String> targets = new TreeSet<>();
        targets.add(label);
        Map<BazelLabel, Set<AspectTargetInfo>> aspectMap =
                workspaceRunner.getAspectTargetInfos(targets, new MockWorkProgressMonitor(), "testWorkspaceRunner");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(4, aspectMap.get(new BazelLabel(label)).size());

        // run a clean, should not throw an exception
        workspaceRunner.runBazelClean(new MockWorkProgressMonitor());
    }
}
