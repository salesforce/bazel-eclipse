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

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.command.mock.MockWorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.mock.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.eclipse.model.AspectPackageInfo;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

public class BazelWorkspaceCommandRunnerTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testGlobalRunner() throws Exception {
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(tmpFolder.newFolder());

        // verify that the command runner has the Bazel exec path
        assertEquals(env.bazelExecutable.getAbsolutePath(), BazelWorkspaceCommandRunner.getBazelExecutablePath());

        // run our version check, will throw if version is not approved
        BazelWorkspaceCommandRunner globalRunner = env.globalCommandRunner;
        globalRunner.runBazelVersionCheck();
    }

    @Test
    public void testWorkspaceRunner() throws Exception {
        File testDir = tmpFolder.newFolder();
        File workspaceDir = new File(testDir, "bazel-workspace");
        workspaceDir.mkdirs();
        File outputbaseDir = new File(testDir, "outputbase");
        outputbaseDir.mkdirs();
        TestBazelWorkspaceFactory workspace = new TestBazelWorkspaceFactory(workspaceDir, outputbaseDir).javaPackages(3).build();
        TestBazelCommandEnvironmentFactory env = new TestBazelCommandEnvironmentFactory();
        env.createTestEnvironment(workspace, testDir, null);

        // verify that the command runner has the Bazel exec path
        assertEquals(env.bazelExecutable.getAbsolutePath(), BazelWorkspaceCommandRunner.getBazelExecutablePath());

        BazelWorkspaceCommandRunner workspaceRunner = env.bazelWorkspaceCommandRunner;
        
        // test getting aspects from the file system
        Set<String> targets = new TreeSet<>();
        targets.add("//projects/libs/javalib0:*");
        Map<String, Set<AspectPackageInfo>> aspectMap = workspaceRunner.getAspectPackageInfos("javalib0", targets, new MockWorkProgressMonitor(),
            "testWorkspaceRunner");
        // aspect infos returned for: guava, slf4j, javalib0, javalib0-test
        assertEquals(4, aspectMap.get("//projects/libs/javalib0:*").size());
        
        // run a clean, should not throw an exception
        workspaceRunner.runBazelClean(new MockWorkProgressMonitor());
    }
}
