package com.salesforce.bazel.eclipse.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.eclipse.mock.MockEnvironmentBuilder;

/**
 * This FTest checks that the Eclipse workspace and Eclipse projects are configured as expected
 * after an import. Other tests overlap with this on deeper verifications (classpath, etc) but this
 * one is looking at the basic workspace/project configs.
 */
public class BazelEclipseProjectFactoryFTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();
    
    
    private IProject workspace_IProject;
    private IProject javalib0_IProject;
    private IProject javalib1_IProject;

    @Test
    public void testImportWorkspace() throws Exception {
        File testTempDir = tmpFolder.newFolder();
        // during test development, it can be useful to have a stable location on disk for the Bazel workspace contents
        //testTempDir = new File("/tmp/bef/bazelws");
        //testTempDir.mkdirs();

        // create the mock Eclipse runtime in the correct state, which is two java projects javalib0 and javalib1
        int numberOfJavaPackages = 2;
        boolean computeClasspaths = true;
        MockEclipse mockEclipse = MockEnvironmentBuilder.createMockEnvironment_Imported_All_JavaPackages(testTempDir, numberOfJavaPackages, computeClasspaths);
        workspace_IProject = mockEclipse.getImportedProject("Bazel Workspace ("+MockEclipse.BAZEL_WORKSPACE_NAME+")");
        javalib0_IProject = mockEclipse.getImportedProject("javalib0");
        javalib1_IProject = mockEclipse.getImportedProject("javalib1");

        // workspace preferences
        IPreferenceStore prefsStore = BazelPluginActivator.getResourceHelper().getPreferenceStore(BazelPluginActivator.getInstance());
        assertEquals(mockEclipse.getBazelWorkspaceRoot().getAbsolutePath(), prefsStore.getString(BazelPluginActivator.BAZEL_WORKSPACE_PATH_PREF_NAME));
        
        // Eclipse project for the Bazel workspace
        assertNotNull(workspace_IProject);
        assertEquals("Bazel Workspace ("+MockEclipse.BAZEL_WORKSPACE_NAME+")", workspace_IProject.getName());
        // at some point we may drop the Java nature from the Bazel Workspace project, but until then...
        IProjectDescription workspace_description = BazelPluginActivator.getResourceHelper().getProjectDescription(workspace_IProject);
        hasNature("workspace", workspace_description.getNatureIds(), true, true);
        
        // Eclipse project for a Java package: javalib0
        assertNotNull(javalib0_IProject);
        assertEquals("javalib0", javalib0_IProject.getName());
        IProjectDescription javalib0_description = BazelPluginActivator.getResourceHelper().getProjectDescription(javalib0_IProject);
        hasNature("javalib0", javalib0_description.getNatureIds(), true, true);

        // Eclipse project for a Java package: javalib1
        assertNotNull(javalib1_IProject);
        assertEquals("javalib1", javalib1_IProject.getName());
        IProjectDescription javalib1_description = BazelPluginActivator.getResourceHelper().getProjectDescription(javalib1_IProject);
        hasNature("javalib1", javalib1_description.getNatureIds(), true, true);
        
        // javalib1 depends on javalib0 (Bazel-wise) which should get imported as a Project reference
        IProject[] javalib1_refs = javalib1_description.getReferencedProjects();
        assertEquals(1, javalib1_refs.length);
        assertEquals("javalib0", javalib1_refs[0].getName());
    }
    
    private void hasNature(String projectName, String[] natureIds, boolean expectJava, boolean expectBazel) {
        boolean hasJava = false;
        boolean hasBazel = false;
        for (String id : natureIds) {
            if (id.equals(JavaCore.NATURE_ID)) {
                hasJava = true;
            } else if (id.equals(BazelNature.BAZEL_NATURE_ID)) {
                hasBazel = true;
            } else {
                fail("Unexpected nature ["+id+"] on project "+projectName);
            }
        }
        assertEquals(expectJava, hasJava);
        assertEquals(expectBazel, hasBazel);
    }
}
