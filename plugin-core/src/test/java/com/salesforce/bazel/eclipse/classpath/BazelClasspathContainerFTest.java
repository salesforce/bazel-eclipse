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
package com.salesforce.bazel.eclipse.classpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.salesforce.bazel.eclipse.mock.EclipseFunctionalTestEnvironmentFactory;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;

public class BazelClasspathContainerFTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();
    
    private IProject workspace_IProject;
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
     * We create an Eclipse project for the Bazel Workspace with a Java project.
     * <p>
     * Variant: implicit deps are enabled in the .bazelrc (explicit_java_test_deps=false) which is the default
     * See ImplicitDependencyHelper.java for more details on implicit deps.
     */
    @Test
    public void testClasspath_BazelJavaProject_implicitdeps() throws Exception {
        // SETUP
        // javalib0 is the base Java library created by our test harness
        // javalib1 is the second Java library created by our test harness, and it is made to depend on javalib0
        boolean explicitJavaTestDeps = false;
        setupMockEnvironmentForClasspathTest("tcpbjp_im", explicitJavaTestDeps);
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
        
        // SECOND check that the resolved classpath has 3 entries for javalib0: (TestRunner comes from implicit deps)
        // bazel-output-base/execroot/mock_workspace/external/com_google_guava_guava/jar/guava-20.0.jar
        // bazel-output-base/execroot/mock_workspace/external/org_slf4j_slf4j_api/jar/slf4j-api-1.7.25.jar
        // bazel-output-base/execroot/test_workspace/bazel-out/darwin-fastbuild/bin/external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_linux/java_tools/Runner_deploy-ijar.jar
        entries = javaHelper.getResolvedClasspath(javalib0_IJavaProject, false);
        assertNotNull(entries);
        //printClasspathEntries("tcpbjp_im", entries);
        assertEquals(3, entries.length);
        assertTrue(entries[0].getPath().toString().contains("guava"));
        assertTrue(entries[1].getPath().toString().contains("slf4j"));
        assertTrue(entries[2].getPath().toString().contains("Runner")); // this is the magical implicit dep TestRunner jar that adds hamcrest, junit, javax.annotation to cp

        // THIRD check that the resolved classpath has 3 entries for javalib1: (TestRunner comes from implicit deps)
        // bazel-output-base/execroot/mock_workspace/external/com_google_guava_guava/jar/guava-20.0.jar
        // bazel-output-base/execroot/mock_workspace/external/org_slf4j_slf4j_api/jar/slf4j-api-1.7.25.jar
        // javalib0
        // bazel-output-base/execroot/test_workspace/bazel-out/darwin-fastbuild/bin/external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_linux/java_tools/Runner_deploy-ijar.jar
        entries = javaHelper.getResolvedClasspath(javalib1_IJavaProject, false);
        assertNotNull(entries);
        // printClasspathEntries(entries);
        assertEquals(4, entries.length);
        // make sure we picked up the inter-project dep (javalib1 depends on javalib0)
        assertEquals("javalib0", entries[2].getPath().toString());
    }

    /**
     * We create an Eclipse project for the Bazel Workspace with a Java project.
     * <p>
     * Variant: explicit deps are enabled in the .bazelrc (explicit_java_test_deps=true) which is the best practice
     * but not default setting. This config requires the BUILD file to specifically add junit/hamcrest to the deps
     * for the java_test rules
     */
    @Test
    public void testClasspath_BazelJavaProject_explicitdeps() throws Exception {
        // SETUP
        // javalib0 is the base Java library created by our test harness
        // javalib1 is the second Java library created by our test harness, and it is made to depend on javalib0
        boolean explicitJavaTestDeps = true;
        setupMockEnvironmentForClasspathTest("tcpbjp_ex", explicitJavaTestDeps);
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
        //printClasspathEntries("tcpbjp_ex", entries);
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
     * We create an Eclipse project for the Bazel Workspace as a container, make sure we return empty results for 
     * classpath entries for the Workspace project.
     */
    @Test
    public void testClasspath_BazelWorkspaceProject() throws Exception {
        boolean explicitJavaTestDeps = false;
        setupMockEnvironmentForClasspathTest("tcpbwp", explicitJavaTestDeps);
        ResourceHelper resourceHelper = mock(ResourceHelper.class);
        when(resourceHelper.isBazelRootProject(workspace_IProject)).thenReturn(true);

        BazelClasspathContainer classpathContainer = new BazelClasspathContainer(workspace_IProject, resourceHelper);
        IClasspathEntry[] entries = classpathContainer.getClasspathEntries();

        assertNotNull(entries);
        assertEquals(0, entries.length);
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
    
    
    // HELPERS
    
    /**
     * Print the sorted list of classpath entries, useful during test development
     */
    @SuppressWarnings("unused")
    private void printClasspathEntries(String testName, IClasspathEntry[] entries) {
        Set<String> paths = new TreeSet<>();
        for (IClasspathEntry entry : entries) {
            paths.add(entry.getPath().toString());
        }
        for (String path : paths) {
            System.out.println("CP ["+testName+"]: "+path);
        }
        
    }
}
