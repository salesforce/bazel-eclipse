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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.mock.EclipseFunctionalTestEnvironmentFactory;
import com.salesforce.bazel.eclipse.mock.MockEclipse;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterFactory;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

public class BazelClasspathContainerFTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private IProject workspace_IProject;
    private IProject javalib0_IProject;
    private IJavaProject javalib0_IJavaProject;
    private IProject javalib1_IProject;
    private IJavaProject javalib1_IJavaProject;

    @Before
    public void setup() {
        // reset this global flag because the import flow checks to make sure an import isnt already in progress
        ProjectImporterFactory.importInProgress.set(false);
    }

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
     * Variant: implicit deps are enabled in the .bazelrc (explicit_java_test_deps=false) which is the default See
     * ImplicitDependencyHelper.java for more details on implicit deps.
     */
    @Test
    public void testClasspath_BazelJavaProject_implicitdeps() throws Exception {
        // SETUP
        // javalib0 is the base Java library created by our test harness
        // javalib1 is the second Java library created by our test harness, and it is made to depend on javalib0
        boolean explicitJavaTestDeps = false;

        setupMockEnvironmentForClasspathTest("tcpbjp_im", explicitJavaTestDeps, false, false, false);
        JavaCoreHelper javaHelper = BazelPluginActivator.getJavaCoreHelper();

        // FIRST check that the project raw classpath has 4 entries for javalib0:
        IClasspathEntry[] entries = javaHelper.getRawClasspath(javaHelper.getJavaProjectForProject(javalib0_IProject));
        assertNotNull(entries);
        printClasspathEntries("tcpbjp_im RAW", entries);
        assertContainsEntry(entries, "javalib0/src/main/java", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/src/main/resources", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/src/test/java", EXACT, TESTCP);
        assertContainsEntry(entries, "javalib0/src/test/resources", EXACT, TESTCP);
        assertContainsEntry(entries, "com.salesforce.bazel.eclipse.BAZEL_CONTAINER", EXACT, MAINCP);
        assertContainsEntry(entries, "JavaSE-11", CONTAINS, MAINCP);

        // SECOND check that the resolved classpath has 3 entries for javalib0: (TestRunner comes from implicit deps)
        entries = javaHelper.getResolvedClasspath(javalib0_IJavaProject, false);
        assertNotNull(entries);
        printClasspathEntries("tcpbjp_im RESOLVED0", entries);
        assertEquals(3, entries.length);
        assertContainsEntry(entries, "guava", CONTAINS, MAINCP);
        assertContainsEntry(entries, "slf4j", CONTAINS, MAINCP);
        assertContainsEntry(entries, "Runner", CONTAINS, TESTCP); // this is the magical implicit dep TestRunner jar that adds hamcrest, junit, javax.annotation to cp

        // THIRD check that the resolved classpath has 3 entries for javalib1: (TestRunner comes from implicit deps)
        entries = javaHelper.getResolvedClasspath(javalib1_IJavaProject, false);
        assertNotNull(entries);
        printClasspathEntries("tcpbjp_im RESOLVED1", entries);
        assertEquals(4, entries.length);
        assertContainsEntry(entries, "guava", CONTAINS, MAINCP);
        assertContainsEntry(entries, "slf4j", CONTAINS, MAINCP);
        assertContainsEntry(entries, "Runner", CONTAINS, TESTCP);
        // make sure we picked up the inter-project dep (javalib1 depends on javalib0)
        assertContainsEntry(entries, "javalib0", EXACT, MAINCP);
    }

    /**
     * We create an Eclipse project for the Bazel Workspace with a Java project.
     * <p>
     * Variant: explicit deps are enabled in the .bazelrc (explicit_java_test_deps=true) which is the best practice but
     * not default setting. This config requires the BUILD file to specifically add junit/hamcrest to the deps for the
     * java_test rules
     */
    @Test
    public void testClasspath_BazelJavaProject_explicitdeps() throws Exception {
        // SETUP
        // javalib0 is the base Java library created by our test harness
        // javalib1 is the second Java library created by our test harness, and it is made to depend on javalib0
        boolean explicitJavaTestDeps = true;

        setupMockEnvironmentForClasspathTest("tcpbjp_ex", explicitJavaTestDeps, false, false, false);
        JavaCoreHelper javaHelper = BazelPluginActivator.getJavaCoreHelper();

        // FIRST check that the project raw classpath has 6 entries for javalib0:
        IClasspathEntry[] entries = javaHelper.getRawClasspath(javaHelper.getJavaProjectForProject(javalib0_IProject));
        assertNotNull(entries);
        printClasspathEntries("tcpbjp_ex1", entries);
        assertEquals(6, entries.length);
        assertContainsEntry(entries, "javalib0/src/main/java", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/src/main/resources", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/src/test/java", EXACT, TESTCP);
        assertContainsEntry(entries, "javalib0/src/test/resources", EXACT, TESTCP);
        assertContainsEntry(entries, "com.salesforce.bazel.eclipse.BAZEL_CONTAINER", EXACT, MAINCP);
        assertContainsEntry(entries, "JavaSE-11", CONTAINS, MAINCP);

        // SECOND check that the resolved classpath has 4 entries for javalib0:
        entries = javaHelper.getResolvedClasspath(javalib0_IJavaProject, false);
        assertNotNull(entries);
        printClasspathEntries("tcpbjp_ex2", entries);
        assertEquals(4, entries.length);
        IClasspathEntry guavaEntry = assertContainsEntry(entries, "guava", CONTAINS, MAINCP);
        assertContainsEntry(entries, "slf4j", CONTAINS, MAINCP);
        assertContainsEntry(entries, "junit-4.12", CONTAINS, TESTCP);
        assertContainsEntry(entries, "hamcrest", CONTAINS, TESTCP);

        // ASIDE lets make sure maven install jars come through with their source jar
        IPath guavaSourcePath = guavaEntry.getSourceAttachmentPath();
        assertNotNull(guavaSourcePath);
        assertTrue(guavaSourcePath.toOSString().endsWith("guava-20.0-sources.jar"));

        // THIRD check that the resolved classpath has 5 entries for javalib1:
        entries = javaHelper.getResolvedClasspath(javalib1_IJavaProject, false);
        assertNotNull(entries);
        printClasspathEntries("tcpbjp_ex3", entries);
        assertEquals(5, entries.length);
        assertContainsEntry(entries, "guava", CONTAINS, MAINCP);
        assertContainsEntry(entries, "slf4j", CONTAINS, MAINCP);
        assertContainsEntry(entries, "junit-4.12", CONTAINS, TESTCP);
        assertContainsEntry(entries, "hamcrest", CONTAINS, TESTCP);
        // make sure we picked up the inter-project dep (javalib1 depends on javalib0)
        assertContainsEntry(entries, "javalib0", EXACT, MAINCP);
    }

    /**
     * We create an Eclipse project for the Bazel Workspace with a Java project.
     * <p>
     * Variant: this java package uses a non-Maven layout. It has source/dev/java and source/test/java.
     */
    @Test
    public void testClasspath_BazelJavaProject_nonstandardLayout() throws Exception {
        // SETUP
        // javalib0 is the base Java library created by our test harness
        // javalib1 is the second Java library created by our test harness, and it is made to depend on javalib0

        boolean explicitJavaTestDeps = true;
        boolean nonstandardlayout = true;
        boolean nonstandardlayout_multipledirs = false;
        boolean addJavaImport = false;

        setupMockEnvironmentForClasspathTest("tcpbjp_ex", explicitJavaTestDeps, nonstandardlayout,
            nonstandardlayout_multipledirs, addJavaImport);

        JavaCoreHelper javaHelper = BazelPluginActivator.getJavaCoreHelper();

        // Check that the project raw classpath has 4 entries for javalib0:
        IClasspathEntry[] entries = javaHelper.getRawClasspath(javaHelper.getJavaProjectForProject(javalib0_IProject));
        assertNotNull(entries);
        printClasspathEntries("testClasspath_BazelJavaProject_nonstandardLayout", entries);

        assertEquals(4, entries.length);
        assertContainsEntry(entries, "javalib0/source/dev/java", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/source/test/java", EXACT, TESTCP);
        assertContainsEntry(entries, "com.salesforce.bazel.eclipse.BAZEL_CONTAINER", EXACT, MAINCP);
        assertContainsEntry(entries, "JavaSE-11", CONTAINS, MAINCP);
    }


    /**
     * We create an Eclipse project for the Bazel Workspace with a Java project.
     * <p>
     * Variant: this java package uses a non-Maven layout. It has source/dev/java, source/dev2/java, source/test/java,
     * source/test2/java.
     */
    @Test
    public void testClasspath_BazelJavaProject_nonstandardLayout_multiple() throws Exception {
        // SETUP
        // javalib0 is the base Java library created by our test harness
        // javalib1 is the second Java library created by our test harness, and it is made to depend on javalib0

        boolean explicitJavaTestDeps = true;
        boolean nonstandardlayout = true;
        boolean nonstandardlayout_multipledirs = true;
        boolean addJavaImport = false;

        setupMockEnvironmentForClasspathTest("tcpbjp_ex", explicitJavaTestDeps, nonstandardlayout,
            nonstandardlayout_multipledirs, addJavaImport);

        JavaCoreHelper javaHelper = BazelPluginActivator.getJavaCoreHelper();

        // Check that the project raw classpath has 6 entries for javalib0:
        IClasspathEntry[] entries = javaHelper.getRawClasspath(javaHelper.getJavaProjectForProject(javalib0_IProject));
        assertNotNull(entries);
        printClasspathEntries("testClasspath_BazelJavaProject_nonstandardLayout_multiple", entries);

        assertEquals(6, entries.length);
        assertContainsEntry(entries, "javalib0/source/dev/java", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/source/dev2/java", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/source/test/java", EXACT, TESTCP);
        assertContainsEntry(entries, "javalib0/source/test2/java", EXACT, TESTCP);
        assertContainsEntry(entries, "com.salesforce.bazel.eclipse.BAZEL_CONTAINER", EXACT, MAINCP);
        assertContainsEntry(entries, "JavaSE-11", CONTAINS, MAINCP);
    }

    /**
     * We create an Eclipse project for the Bazel Workspace with a Java project.
     * <p>
     * Variant: this java package has a java_import to bring in a jar file from the file system
     */
    @Test
    public void testClasspath_BazelJavaProject_javaimport() throws Exception {
        // SETUP
        // javalib0 is the base Java library created by our test harness
        // javalib1 is the second Java library created by our test harness, and it is made to depend on javalib0

        boolean explicitJavaTestDeps = true;
        boolean nonstandardlayout = false;
        boolean nonstandardlayout_multipledirs = false;
        boolean addJavaImport = true;

        setupMockEnvironmentForClasspathTest("tcpbjp_ex", explicitJavaTestDeps, nonstandardlayout,
            nonstandardlayout_multipledirs, addJavaImport);

        JavaCoreHelper javaHelper = BazelPluginActivator.getJavaCoreHelper();

        // FIRST heck that the project raw classpath has 6 entries for javalib0:
        IClasspathEntry[] entries = javaHelper.getRawClasspath(javaHelper.getJavaProjectForProject(javalib0_IProject));
        assertNotNull(entries);
        printClasspathEntries("testClasspath_BazelJavaProject_javaimport1", entries);
        assertContainsEntry(entries, "javalib0/src/main/java", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/src/main/resources", EXACT, MAINCP);
        assertContainsEntry(entries, "javalib0/src/test/java", EXACT, TESTCP);
        assertContainsEntry(entries, "javalib0/src/test/resources", EXACT, TESTCP);
        assertContainsEntry(entries, "com.salesforce.bazel.eclipse.BAZEL_CONTAINER", EXACT, MAINCP);
        assertContainsEntry(entries, "JavaSE-11", CONTAINS, MAINCP);

        // SECOND check that the resolved classpath has 5 entries for javalib0:
        entries = javaHelper.getResolvedClasspath(javalib0_IJavaProject, false);
        assertNotNull(entries);
        printClasspathEntries("testClasspath_BazelJavaProject_javaimport2", entries);
        assertEquals(5, entries.length);
        assertContainsEntry(entries, "guava", CONTAINS, MAINCP);
        assertContainsEntry(entries, "slf4j", CONTAINS, MAINCP);
        assertContainsEntry(entries, "junit-4.12", CONTAINS, TESTCP);
        assertContainsEntry(entries, "hamcrest", CONTAINS, TESTCP);

        // the java_import rule brings in liborange.jar to the classpath
        IClasspathEntry importEntry = assertContainsEntry(entries, "liborange.jar", CONTAINS, MAINCP);
        // the test workspace factory also adds a source jar to the java_import rule
        IPath importSourcePath = importEntry.getSourceAttachmentPath();
        assertNotNull(importSourcePath);
        assertTrue(importSourcePath.toOSString().endsWith("liborange-src.jar"));
    }

    /**
     * We create an Eclipse project for the Bazel Workspace as a container, make sure we return empty results for
     * classpath entries for the Workspace project.
     */
    @Test
    public void testClasspath_BazelWorkspaceProject() throws Exception {
        boolean explicitJavaTestDeps = false;
        setupMockEnvironmentForClasspathTest("tcpbwp", explicitJavaTestDeps, false, false, false);
        ResourceHelper resourceHelper = mock(ResourceHelper.class);
        when(resourceHelper.isBazelRootProject(workspace_IProject)).thenReturn(true);

        BazelClasspathContainer classpathContainer = new BazelClasspathContainer(workspace_IProject, resourceHelper);
        IClasspathEntry[] entries = classpathContainer.getClasspathEntries();

        assertNotNull(entries);
        assertEquals(0, entries.length);
    }

    // HELPERS

    private MockEclipse setupMockEnvironmentForClasspathTest(String testName, boolean explicitJavaTestDeps,
            boolean nonstandardLayout, boolean nonstandardMultipleDirs, boolean addJavaImport)
                    throws Exception {
        File testDir = tmpFolder.newFolder();
        File testTempDir = new File(testDir, testName);
        testTempDir.mkdirs();

        // during test development, it can be useful to have a stable location on disk for the Bazel workspace contents
        //testTempDir = new File("/tmp/bef/bazelws");
        //testTempDir.mkdirs();

        // create the mock Eclipse runtime in the correct state

        TestOptions testOptions = new TestOptions().numberOfJavaPackages(2).computeClasspaths(true)
                .explicitJavaTestDeps(explicitJavaTestDeps).nonStandardJavaLayout_enabled(nonstandardLayout)
                .nonStandardJavaLayout_multipledirs(nonstandardMultipleDirs).addJavaImportRule(addJavaImport);

        MockEclipse mockEclipse =
                EclipseFunctionalTestEnvironmentFactory.createMockEnvironment_Imported_All_JavaPackages(testTempDir,
                    testOptions);

        workspace_IProject =
                mockEclipse.getImportedProject("Bazel Workspace (" + MockEclipse.BAZEL_WORKSPACE_NAME + ")");
        javalib0_IProject = mockEclipse.getImportedProject("javalib0");
        javalib0_IJavaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(javalib0_IProject);
        javalib1_IProject = mockEclipse.getImportedProject("javalib1");
        javalib1_IJavaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(javalib1_IProject);

        assertEquals(2, mockEclipse.getBazelWorkspaceCreator().workspaceDescriptor.createdPackages.size());

        return mockEclipse;
    }

    // HELPERS

    private static final boolean EXACT = false;
    private static final boolean CONTAINS = true;
    private static final boolean MAINCP = false;
    private static final boolean TESTCP = true;

    private IClasspathEntry assertContainsEntry(IClasspathEntry[] entries, String path, boolean matchUsingContains,
            boolean isTestCP) {
        for (IClasspathEntry entry : entries) {
            // NOTE: classpath paths dont need to be Windows escaped, they are already converted to unix style paths
            String epath = entry.getPath().toString();
            boolean match = (matchUsingContains && epath.contains(path)) || (!matchUsingContains && epath.equals(path));
            if (match) {
                if (isTestCP) {
                    assertTrue(entry.isTest());
                    IPath outputLoc = entry.getOutputLocation();
                    if (outputLoc != null) {
                        assertTrue(outputLoc.toOSString().endsWith("testbin"));
                    }
                } else {
                    assertFalse(entry.isTest());
                }
                return entry;
            }
        }
        fail("Entry " + path + " was not found in the classpath.");
        return null;
    }

    /**
     * Print the sorted list of classpath entries, useful during test development
     */
    @SuppressWarnings("unused")
    private void printClasspathEntries(String testName, IClasspathEntry[] entries) {
        int index = 0;
        for (IClasspathEntry entry : entries) {
            String path = entry.getPath().toOSString();
            int ob = path.indexOf("outputbase");
            if (ob > 0) {
                path = "..." + path.substring(ob);
            }
            System.err.println("CP[" + index++ + "] [" + testName + "]: " + path + " (testCP=" + entry.isTest() + ")");
        }

    }
}
