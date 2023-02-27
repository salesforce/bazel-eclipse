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
package com.salesforce.bazel.eclipse.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.core.BazelCorePluginSharedContstants;
import com.salesforce.bazel.eclipse.mock.EclipseFunctionalTestEnvironmentFactory;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * This FTest checks that the Eclipse workspace and Eclipse projects are configured as expected after an import. Other
 * tests overlap with this on deeper verifications (classpath, etc) but this one is looking at the basic
 * workspace/project configs.
 */
public class BazelEclipseProjectFactoryFTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private IProject workspace_IProject;
    private IJavaProject workspace_IJavaProject;
    private IProject javalib0_IProject;
    private IJavaProject javalib0_IJavaProject;
    private IProject javalib1_IProject;

    @Test
    @Ignore("Disabled due to required Mocking rework")
    public void testImportWorkspace() throws Exception {
        var testTempDir = tmpFolder.newFolder();
        // during test development, it can be useful to have a stable location on disk for the Bazel workspace contents
        //testTempDir = new File("/tmp/bef/bazelws");
        //testTempDir.mkdirs();

        // create the mock Eclipse runtime in the correct state, which is two java projects javalib0 and javalib1
        var testOptions =
                new TestOptions().uniqueKey("imws").numberOfJavaPackages(2).explicitJavaTestDeps(false);
        var wsName = MockEclipse.BAZEL_WORKSPACE_NAME + "-imws";

        var computeClasspaths = true;
        var mockEclipse = EclipseFunctionalTestEnvironmentFactory
                .createMockEnvironment_Imported_All_JavaPackages(testTempDir, testOptions, computeClasspaths);
        workspace_IProject = mockEclipse.getImportedProject("Bazel Workspace (" + wsName + ")");
        assertNotNull(workspace_IProject);
        javalib0_IProject = mockEclipse.getImportedProject("javalib0");
        assertNotNull(javalib0_IProject);
        javalib0_IJavaProject = mockEclipse.getMockJavaCoreHelper().getJavaProjectForProject(javalib0_IProject);
        assertNotNull(javalib0_IJavaProject);
        javalib1_IProject = mockEclipse.getImportedProject("javalib1");
        assertNotNull(javalib1_IProject);

        // workspace preferences
        var configMgr = ComponentContext.getInstance().getConfigurationManager();
        var expectedBazelWorkspaceRoot = mockEclipse.getBazelWorkspaceRoot().getCanonicalPath();
        assertEquals(expectedBazelWorkspaceRoot, configMgr.getBazelWorkspacePath());

        // Eclipse project for the Bazel workspace
        assertNotNull(workspace_IProject);
        assertEquals("Bazel Workspace (" + wsName + ")", workspace_IProject.getName());
        // at some point we may drop the Java nature from the Bazel Workspace project, but until then...
        var workspace_description =
                ComponentContext.getInstance().getResourceHelper().getProjectDescription(workspace_IProject);
        hasNature("workspace", workspace_description.getNatureIds(), true, true);

        // Eclipse project for a Java package: javalib0
        assertNotNull(javalib0_IProject);
        assertEquals("javalib0", javalib0_IProject.getName());
        var javalib0_description =
                ComponentContext.getInstance().getResourceHelper().getProjectDescription(javalib0_IProject);
        hasNature("javalib0", javalib0_description.getNatureIds(), true, true);
        // javalib0 CP should have 4 source folders (src/main/java, src/main/resources, src/test/java, src/test/resources), JDK, and Bazel Classpath Container
        var refClasspathEntries = javalib0_IJavaProject.getReferencedClasspathEntries();
        assertEquals(6, refClasspathEntries.length);

        // Eclipse project for a Java package: javalib1
        assertNotNull(javalib1_IProject);
        assertEquals("javalib1", javalib1_IProject.getName());
        var javalib1_description =
                ComponentContext.getInstance().getResourceHelper().getProjectDescription(javalib1_IProject);
        hasNature("javalib1", javalib1_description.getNatureIds(), true, true);

        // javalib1 depends on javalib0 (Bazel-wise) which should get imported as a Project reference
        var javalib1_refs = javalib1_description.getReferencedProjects();
        assertEquals(1, javalib1_refs.length);
        assertEquals("javalib0", javalib1_refs[0].getName());
    }

    @Test
    @Ignore("Disabled due to required Mocking rework")
    public void testImportWorkspace_withRootPackage() throws Exception {
        var testTempDir = tmpFolder.newFolder();
        // during test development, it can be useful to have a stable location on disk for the Bazel workspace contents
        //testTempDir = new File("/tmp/bef/bazelws");
        //testTempDir.mkdirs();

        // create the mock Eclipse runtime in the correct state, which is two java projects root-package0 and javalib0
        var testOptions = new TestOptions().uniqueKey("imws").numberOfJavaPackages(1)
                .explicitJavaTestDeps(false).hasRootPackage(true);
        var wsName = MockEclipse.BAZEL_WORKSPACE_NAME + "-imws";

        var computeClasspaths = true;
        var mockEclipse = EclipseFunctionalTestEnvironmentFactory
                .createMockEnvironment_Imported_All_JavaPackages(testTempDir, testOptions, computeClasspaths);
        workspace_IProject = mockEclipse.getImportedProject("Bazel Workspace (" + wsName + ")");
        assertNotNull(workspace_IProject);
        workspace_IJavaProject = mockEclipse.getMockJavaCoreHelper().getJavaProjectForProject(workspace_IProject);
        assertNotNull(workspace_IJavaProject);

        // Eclipse project for the Bazel workspace
        assertNotNull(workspace_IProject);
        assertEquals("Bazel Workspace (" + wsName + ")", workspace_IProject.getName());
        var workspace_description =
                ComponentContext.getInstance().getResourceHelper().getProjectDescription(workspace_IProject);
        hasNature("workspace", workspace_description.getNatureIds(), true, true);
    }

    private void hasNature(String projectName, String[] natureIds, boolean expectJava, boolean expectBazel) {
        var hasJava = false;
        var hasBazel = false;
        for (String id : natureIds) {
            if (id.equals(JavaCore.NATURE_ID)) {
                hasJava = true;
            } else if (id.equals(BazelCorePluginSharedContstants.BAZEL_NATURE_ID)) {
                hasBazel = true;
            } else {
                fail("Unexpected nature [" + id + "] on project " + projectName);
            }
        }
        assertEquals(expectJava, hasJava);
        assertEquals(expectBazel, hasBazel);
    }
}
