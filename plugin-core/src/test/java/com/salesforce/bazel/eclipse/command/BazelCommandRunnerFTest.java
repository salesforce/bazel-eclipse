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

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.mock.EclipseFunctionalTestEnvironmentFactory;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.eclipse.model.BazelWorkspace;

public class BazelCommandRunnerFTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    
    /**
     * At the start of importing a Bazel workspace, the Bazel command runner needs to operational enough
     * to run 'bazel info workspace' and other info commands. 
     * Make sure it is setup sufficiently in the 'pre-import' state.
     */
    @Test
    public void testBazelWorkspaceCommandRunner_AtStartOfImport() throws Exception {
        File testTempDir = tmpFolder.newFolder();

        // create the mock Eclipse runtime in the correct state
        MockEclipse mockEclipse = EclipseFunctionalTestEnvironmentFactory.createMockEnvironment_PriorToImport_JavaPackages(
            testTempDir, 5, false);
        
        // the Bazel commands will run after the bazel root directory is chosen in the UI, so simulate the selection here
        BazelPluginActivator.getInstance().setBazelWorkspaceRootDirectory("test", mockEclipse.getBazelWorkspaceRoot());
        
        // run the method under test
        BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        
        // verify
        assertNotNull(bazelWorkspaceCmdRunner);
        
        File expectedBazelWorkspaceRoot = mockEclipse.getBazelWorkspaceRoot();
        assertEquals(expectedBazelWorkspaceRoot.getAbsolutePath(), BazelPluginActivator.getBazelWorkspaceRootDirectory().getAbsolutePath());
        
        // verify command runner 'bazel info' commands
        File expectedBazelOutputBase = mockEclipse.getBazelOutputBase();
        assertEquals(expectedBazelOutputBase.getAbsolutePath(), bazelWorkspace.getBazelOutputBaseDirectory().getAbsolutePath());
        File expectedBazelExecutionRoot = mockEclipse.getBazelExecutionRoot();
        assertEquals(expectedBazelExecutionRoot.getAbsolutePath(), bazelWorkspace.getBazelExecRootDirectory().getAbsolutePath());
    }
}
