package com.salesforce.bazel.eclipse.builder;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.command.test.MockCommandBuilder;
import com.salesforce.bazel.eclipse.mock.EclipseFunctionalTestEnvironmentFactory;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;

@SuppressWarnings("unused")
public class BazelBuilderFTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();
    
    private IProject workspace_IProject;
    private IProject javalib0_IProject;
    private IJavaProject javalib0_IJavaProject;
    private IProject javalib1_IProject;
    private IJavaProject javalib1_IJavaProject;

    @Test
    @Ignore // still need to fix the publishProblemMarkers() issue "Workspace is closed"
    public void testBazelBuilder_Success() throws Exception {
        MockEclipse mockEclipse = setupMockEnvironmentForClasspathTest("testBazelBuilder_Success", true);
        MockCommandBuilder mockCommandBuilder = mockEclipse.getMockCommandBuilder();
        
        BazelBuilder bazelBuilder = createTestBazelBuilder(javalib1_IProject);
        IProject builderProject = bazelBuilder.getProject();
        assertEquals(javalib1_IProject.getName(), builderProject.getName());
        
        bazelBuilder.build(1, null, new EclipseWorkProgressMonitor());
    }
    
    @Test
    public void testBazelBuilder_Fail1() throws Exception {
        // TODO add some failure tests of BazelBuilder, including Problem Markers
        // TODO try to find a way to repro the TreeSet issue at line 108 of BazelBuilder
    }
    
    // HELPERS

    private MockEclipse setupMockEnvironmentForClasspathTest(String testName, boolean explicitJavaTestDeps) throws Exception {
        File testDir = tmpFolder.newFolder();
        File testTempDir = new File(testDir, testName);
        testTempDir.mkdirs();
        
        // during test development, it can be useful to have a stable location on disk for the Bazel workspace contents
        //testTempDir = new File("/tmp/bef/bazelws");
        //testTempDir.mkdirs();

        // create the mock Eclipse runtime in the correct state
        int numberOfJavaPackages = 2;
        boolean computeClasspaths = true; 
        MockEclipse mockEclipse = EclipseFunctionalTestEnvironmentFactory.createMockEnvironment_Imported_All_JavaPackages(
            testTempDir, numberOfJavaPackages, computeClasspaths, explicitJavaTestDeps);
        
        workspace_IProject = mockEclipse.getImportedProject("Bazel Workspace ("+MockEclipse.BAZEL_WORKSPACE_NAME+")");
        javalib0_IProject = mockEclipse.getImportedProject("javalib0");
        javalib0_IJavaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(javalib0_IProject);
        javalib1_IProject = mockEclipse.getImportedProject("javalib1");
        javalib1_IJavaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(javalib1_IProject);
        
        return mockEclipse;
    }
    
    private BazelBuilder createTestBazelBuilder(IProject project) throws Exception {
        BazelBuilder bazelBuilder = new BazelBuilder();
        
        // yeah, you didn't think it was that easy, did you?
        // we have to wedge in our project, which is not accessible
        TestBuildConfiguration testBuilderConfig = new TestBuildConfiguration(project, project.getName());
        Field f1 = bazelBuilder.getClass().getSuperclass().getSuperclass().getDeclaredField("buildConfiguration");
        f1.setAccessible(true);
        f1.set(bazelBuilder, testBuilderConfig);
        
        return bazelBuilder;
    }
    
    /**
     * We have to inject some state into the private internals of the Builder hierarchy, this is it.
     */
    private static class TestBuildConfiguration implements IBuildConfiguration {
        public IProject project;
        public String name;
        
        public TestBuildConfiguration(IProject project, String name) {
            this.project = project;
            this.name = name;
        }
        
        @Override
        public <T> T getAdapter(Class<T> adapter) {
            return null;
        }

        @Override
        public IProject getProject() {
            return project;
        }

        @Override
        public String getName() {
            return name;
        }
    }
    
}
