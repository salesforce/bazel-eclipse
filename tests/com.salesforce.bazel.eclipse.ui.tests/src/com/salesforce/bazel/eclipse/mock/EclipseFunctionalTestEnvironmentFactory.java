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
 */
package com.salesforce.bazel.eclipse.mock;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporter;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterFactory;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.model.BazelPackageInfoOld;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Factory for creating test environments for Eclipse functional tests. Produces a Mock Eclipse workspace from
 * templates.
 */
public class EclipseFunctionalTestEnvironmentFactory {

    /**
     * Creates an environment with a Bazel workspace with Java packages on disk, but nothing has been imported yet into
     * Eclipse. Includes:
     * <p>
     * <ul>
     * <li>A Bazel workspace on disk
     * <li>Bazel command runner prepared to run 'bazel info workspace' which is done during Bazel Import
     * <li>Everything else in base initialized state
     * </ul>
     */
    public static MockEclipse createMockEnvironment_PriorToImport_JavaPackages(File testTempDir,
            TestOptions testOptions) throws Exception {

        // initialize the SDK support for Java rules
        JvmRuleInit.initialize();

        // build out a Bazel workspace with specified number of Java packages, and a couple of genrules packages just to test that they get ignored
        var wsDir = new File(testTempDir, MockEclipse.BAZEL_WORKSPACE_NAME + "-" + testOptions.uniqueKey);
        wsDir.mkdirs();
        var outputbaseDir = new File(testTempDir, "obase-" + testOptions.uniqueKey);
        outputbaseDir.mkdirs();

        var descriptor =
                new TestBazelWorkspaceDescriptor(wsDir, outputbaseDir).testOptions(testOptions);
        var bazelWorkspaceCreator = new TestBazelWorkspaceFactory(descriptor);
        bazelWorkspaceCreator.build();

        // create the mock Eclipse runtime
        var mockEclipse = new MockEclipse(bazelWorkspaceCreator, testTempDir);

        // the Bazel commands will run after the bazel root directory is chosen in the UI, so simulate the selection here
        BazelCorePlugin.getInstance().setBazelWorkspaceRootDirectory("test", mockEclipse.getBazelWorkspaceRoot());

        return mockEclipse;
    }

    /**
     * Creates an environment with a Bazel workspace with Java packages on disk, and the Java packages have been
     * imported as Eclipse projects. Includes:
     * <p>
     * <ul>
     * <li>A Bazel workspace on disk
     * <li>Each Bazel Java package is an imported Eclipse Java project with Bazel nature
     * </ul>
     */
    public static MockEclipse createMockEnvironment_Imported_All_JavaPackages(File testTempDir, TestOptions testOptions,
            boolean computeClasspaths) throws Exception {
        // create base configuration, which includes the real bazel workspace on disk
        var mockEclipse = createMockEnvironment_PriorToImport_JavaPackages(testTempDir, testOptions);

        // scan the bazel workspace filesystem to build the list of Java projects
        var scanner = new BazelWorkspaceScanner();
        var workspaceRootProject = scanner.getPackages(mockEclipse.getBazelWorkspaceRoot(), null);

        // choose the list of Bazel packages to import, in this case we assume the user selected all Java packages
        List<BazelPackageLocation> bazelPackagesToImport = new ArrayList<>();
        if (testOptions.hasRootPackage) {
            bazelPackagesToImport.add(workspaceRootProject);
        }
        addChildPackagesToImportList(workspaceRootProject, bazelPackagesToImport);

        var projectImporterFactory =
                new ProjectImporterFactory(workspaceRootProject, bazelPackagesToImport);
        projectImporterFactory.setImportOrderResolver(new MockImportOrderResolver());
        projectImporterFactory.skipJREWarmup();
        projectImporterFactory.skipQueryCacheWarmup();
        var projectImporter = projectImporterFactory.build();
        // run the import process (this is actually done in BazelImportWizard.performFinish() when a user is running the show)
        var importedProjectsList = projectImporter.run(new MockProgressMonitor());
        mockEclipse.setImportedProjectsList(importedProjectsList);

        // do you want to simulate Eclipse calling getClasspath on the classpath container for each project?
        if (computeClasspaths) {
            for (IProject project : importedProjectsList) {
                JavaCoreHelper javaHelper = mockEclipse.getMockJavaCoreHelper();
                javaHelper.getResolvedClasspath(javaHelper.getJavaProjectForProject(project), false);
            }
        }

        return mockEclipse;
    }

    private static void addChildPackagesToImportList(BazelPackageInfoOld currentNode,
            List<BazelPackageLocation> bazelPackagesToImport) {
        var children = currentNode.getChildPackageInfos();
        bazelPackagesToImport.addAll(children);
    }

    private static class MockProgressMonitor implements IProgressMonitor {
        @Override
        public void beginTask(String name, int totalWork) {}

        @Override
        public void done() {}

        @Override
        public void internalWorked(double work) {}

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void setCanceled(boolean value) {}

        @Override
        public void setTaskName(String name) {}

        @Override
        public void subTask(String name) {}

        @Override
        public void worked(int work) {}
    }
}
