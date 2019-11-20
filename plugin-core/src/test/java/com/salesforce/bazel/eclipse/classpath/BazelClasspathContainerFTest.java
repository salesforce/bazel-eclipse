package com.salesforce.bazel.eclipse.classpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.eclipse.mock.MockEnvironmentBuilder;
import com.salesforce.bazel.eclipse.runtime.JavaCoreHelper;

public class BazelClasspathContainerFTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();
    
    private IProject workspace_IProject;
    private IJavaProject workspace_JavaIProject;
    private IProject javalib0_IProject;
    private IJavaProject javalib0_IJavaProject;
    private IProject javalib1_IProject;
    private IJavaProject javalib1_IJavaProject;

    /*
     * CLASSPATH README 
     * - the "raw" classpath is the one set at the project level, and includes the Bazel Classpath Container as a single entry, the JRE as a single entry, etc
     * - the "resolved" classpath is the one where each raw entry contributes back the actual elements; for the Bazel classpath container it will contain
     *     an entry PER dependency for the project (e.g. slf4j-api, log4j, other Bazel workspace packages). Each resolved entry is known as a 'simple' entry.
     * - referenced entries are the ones written into the .classpath file, which seem to be the raw classpath at least for our use cases 
     */

    
    
    /**
     * We create an Eclipse project for the Bazel Workspace, make sure we return empty results for classpath entries.
     */
    @Test
    public void testClasspath_BazelJavaProject() throws Exception {
        // SETUP
        // javalib0 is the base Java library created by our test harness
        // javalib1 is the second Java library created by our test harness, and it is made to depend on javalib0
        setupMockEnvironmentForClasspathTest();
        JavaCoreHelper javaHelper = BazelPluginActivator.getJavaCoreHelper();

        // NOTE: classpath entries are ordered lists so they should always be in the same positions

        // FIRST check that the project raw classpath has 4 entries for javalib0:
        // com.salesforce.bazel.eclipse.BAZEL_CONTAINER
        // javalib0/main/java
        // javalib0/test/java
        // org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.8
        IClasspathEntry[] entries = javaHelper.getRawClasspath(javaHelper.getJavaProjectForProject(javalib0_IProject));
        assertNotNull(entries);
        //printClasspathEntries(entries);
        
        // SECOND check that the resolved classpath has 4 entries for javalib0:
        // bazel-output-base/execroot/mock_workspace/external/com_google_guava_guava/jar/guava-20.0.jar
        // bazel-output-base/execroot/mock_workspace/external/junit_junit/jar/junit-4.12.jar
        // bazel-output-base/execroot/mock_workspace/external/org_hamcrest_hamcrest_core/jar/hamcrest-core-1.3.jar
        // bazel-output-base/execroot/mock_workspace/external/org_slf4j_slf4j_api/jar/slf4j-api-1.7.25.jar
        entries = javaHelper.getResolvedClasspath(javalib0_IJavaProject, false);
        assertNotNull(entries);
        //printClasspathEntries(entries);
        assertEquals(4, entries.length);
        assertTrue(entries[0].getPath().toString().contains("guava"));
        assertTrue(entries[1].getPath().toString().contains("junit"));
        assertTrue(entries[2].getPath().toString().contains("hamcrest"));
        assertTrue(entries[3].getPath().toString().contains("slf4j"));

        // THIRD check that the resolved classpath has 5 entries for javalib1:
        // bazel-output-base/execroot/mock_workspace/external/com_google_guava_guava/jar/guava-20.0.jar
        // bazel-output-base/execroot/mock_workspace/external/junit_junit/jar/junit-4.12.jar
        // bazel-output-base/execroot/mock_workspace/external/org_hamcrest_hamcrest_core/jar/hamcrest-core-1.3.jar
        // bazel-output-base/execroot/mock_workspace/external/org_slf4j_slf4j_api/jar/slf4j-api-1.7.25.jar
        // javalib0
        entries = javaHelper.getResolvedClasspath(javalib1_IJavaProject, false);
        assertNotNull(entries);
        // printClasspathEntries(entries);
        assertEquals(5, entries.length);
        // make sure we picked up the inter-project dep (javalib1 depends on javalib0)
        assertEquals("javalib0", entries[4].getPath().toString());
    }

    /**
     * We create an Eclipse project for the Bazel Workspace as a container, make sure we return empty results for classpath entries.
     */
    @Test
    public void testClasspath_BazelWorkspaceProject() throws Exception {
        setupMockEnvironmentForClasspathTest();
                
        BazelClasspathContainer classpathContainer = new BazelClasspathContainer(workspace_IProject, workspace_JavaIProject);
        IClasspathEntry[] entries = classpathContainer.getClasspathEntries();
        
        assertNotNull(entries);
        assertEquals(0, entries.length);
    }

    // HELPERS
    
    private MockEclipse setupMockEnvironmentForClasspathTest() throws Exception {
        File testTempDir = tmpFolder.newFolder();
        
        // during test development, it can be useful to have a stable location on disk for the Bazel workspace contents
        //testTempDir = new File("/tmp/bef/bazelws");
        //testTempDir.mkdirs();

        // create the mock Eclipse runtime in the correct state
        int numberOfJavaPackages = 2;
        boolean computeClasspaths = true; 
        MockEclipse mockEclipse = MockEnvironmentBuilder.createMockEnvironment_Imported_All_JavaPackages(testTempDir, numberOfJavaPackages, computeClasspaths);
        
        workspace_IProject = mockEclipse.getImportedProject("Bazel Workspace ("+MockEclipse.BAZEL_WORKSPACE_NAME+")");
        workspace_JavaIProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(workspace_IProject);
        javalib0_IProject = mockEclipse.getImportedProject("javalib0");
        javalib0_IJavaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(javalib0_IProject);
        javalib1_IProject = mockEclipse.getImportedProject("javalib1");
        javalib1_IJavaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(javalib1_IProject);
        
        return mockEclipse;
    }
    
    
    // HELPERS
    
    /**
     * Print the sorted list of classpath entries, useful during test development
     */
    @SuppressWarnings("unused")
    private void printClasspathEntries(IClasspathEntry[] entries) {
        Set<String> paths = new TreeSet<>();
        for (IClasspathEntry entry : entries) {
            paths.add(entry.getPath().toString());
        }
        for (String path : paths) {
            System.out.println(path);
        }
        
    }
}
