package com.salesforce.bazel.eclipse.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.command.BazelCommandFacade;
import com.salesforce.bazel.eclipse.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.eclipse.mock.MockEnvironmentBuilder;

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
        MockEclipse mockEclipse = MockEnvironmentBuilder.createMockEnvironment_PriorToImport_JavaPackages(testTempDir, 5);
        
        // the Bazel commands will run after the bazel root directory is chosen in the UI, so simulate the selection here
        BazelPluginActivator.getInstance().setBazelWorkspaceRootDirectory(mockEclipse.getBazelWorkspaceRoot());
        
        // run the method under test
        BazelCommandFacade bazelCommandFacade = BazelPluginActivator.getBazelCommandFacade();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandFacade.getWorkspaceCommandRunner(BazelPluginActivator.getBazelWorkspaceRootDirectory());
        
        // verify
        assertNotNull(bazelWorkspaceCmdRunner);
        
        File expectedBazelWorkspaceRoot = mockEclipse.getBazelWorkspaceRoot();
        assertEquals(expectedBazelWorkspaceRoot.getAbsolutePath(), BazelPluginActivator.getBazelWorkspaceRootDirectory().getAbsolutePath());
        
        // verify command runner 'bazel info' commands
        File expectedBazelOutputBase = mockEclipse.getBazelOutputBase();
        assertEquals(expectedBazelOutputBase.getAbsolutePath(), bazelWorkspaceCmdRunner.getBazelWorkspaceOutputBase(null).getAbsolutePath());
        File expectedBazelExecutionRoot = mockEclipse.getBazelExecutionRoot();
        assertEquals(expectedBazelExecutionRoot.getAbsolutePath(), bazelWorkspaceCmdRunner.getBazelWorkspaceExecRoot(null).getAbsolutePath());
    }
}
