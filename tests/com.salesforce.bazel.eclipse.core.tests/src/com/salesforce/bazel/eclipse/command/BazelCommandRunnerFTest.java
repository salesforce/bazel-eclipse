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
 *
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.eclipse.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.activator.BazelPlugin;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.mock.EclipseFunctionalTestEnvironmentFactory;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

public class BazelCommandRunnerFTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    /**
     * At the start of importing a Bazel workspace, the Bazel command runner needs to operational enough to run 'bazel
     * info workspace' and other info commands. Make sure it is setup sufficiently in the 'pre-import' state.
     */
    @Test
    public void testBazelWorkspaceCommandRunner_AtStartOfImport() throws Exception {
        File testTempDir = tmpFolder.newFolder();
        TestOptions testOptions = new TestOptions().uniqueKey("start").numberOfJavaPackages(5)
                .explicitJavaTestDeps(false).useAltConfigFileNames(false);

        // create the mock Eclipse runtime in the correct state
        MockEclipse mockEclipse = EclipseFunctionalTestEnvironmentFactory
                .createMockEnvironment_PriorToImport_JavaPackages(testTempDir, testOptions);

        // the Bazel commands will run after the bazel root directory is chosen in the UI, so simulate the selection here
        BazelPlugin.getInstance().setBazelWorkspaceRootDirectory("test", mockEclipse.getBazelWorkspaceRoot());

        // run the method under test
        BazelWorkspace bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
        BazelCommandManager bazelCommandManager = ComponentContext.getInstance().getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        // verify
        assertNotNull(bazelWorkspaceCmdRunner);

        String expectedBazelWorkspaceRoot = mockEclipse.getBazelWorkspaceRoot().getCanonicalPath();
        String actualBazelWorkspaceRoot =
                EclipseBazelWorkspaceContext.getInstance().getBazelWorkspaceRootDirectory().getCanonicalPath();
        assertEquals(expectedBazelWorkspaceRoot, actualBazelWorkspaceRoot);

        // verify command runner 'bazel info' commands
        String expectedBazelExecutionRoot = mockEclipse.getBazelExecutionRoot().getCanonicalPath();
        String actualBazelExecutionRoot = bazelWorkspace.getBazelExecRootDirectory().getCanonicalPath();
        assertEquals(expectedBazelExecutionRoot, actualBazelExecutionRoot);
    }

    /**
     * Variant of testBazelWorkspaceCommandRunner_AtStartOfImport, but testing acceptance of WORKSPACE.bazel alternative
     * filename
     */
    @Test
    public void testBazelWorkspaceCommandRunner_WorkspaceDotBazel_AtStartOfImport() throws Exception {
        File testTempDir = tmpFolder.newFolder();
        TestOptions testOptions = new TestOptions().uniqueKey("dot").numberOfJavaPackages(5).explicitJavaTestDeps(false)
                .useAltConfigFileNames(true);

        // create the mock Eclipse runtime in the correct state
        MockEclipse mockEclipse = EclipseFunctionalTestEnvironmentFactory
                .createMockEnvironment_PriorToImport_JavaPackages(testTempDir, testOptions);

        // the Bazel commands will run after the bazel root directory is chosen in the UI, so simulate the selection here
        BazelPlugin.getInstance().setBazelWorkspaceRootDirectory("test", mockEclipse.getBazelWorkspaceRoot());

        // run the method under test
        BazelWorkspace bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
        BazelCommandManager bazelCommandManager = ComponentContext.getInstance().getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        // verify
        assertNotNull(bazelWorkspaceCmdRunner);

        String expectedBazelWorkspaceRoot = mockEclipse.getBazelWorkspaceRoot().getCanonicalPath();
        String actualBazelWorkspaceRoot =
                EclipseBazelWorkspaceContext.getInstance().getBazelWorkspaceRootDirectory().getCanonicalPath();
        assertEquals(expectedBazelWorkspaceRoot, actualBazelWorkspaceRoot);

        // verify command runner 'bazel info' commands
        String expectedBazelExecutionRoot = mockEclipse.getBazelExecutionRoot().getCanonicalPath();
        String actualBazelExecutionRoot = bazelWorkspace.getBazelExecRootDirectory().getCanonicalPath();
        assertEquals(expectedBazelExecutionRoot, actualBazelExecutionRoot);
    }
}
