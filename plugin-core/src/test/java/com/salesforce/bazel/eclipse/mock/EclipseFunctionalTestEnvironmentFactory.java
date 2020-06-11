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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;

import com.salesforce.bazel.eclipse.config.BazelEclipseProjectFactory;
import com.salesforce.bazel.eclipse.importer.BazelProjectImportScanner;
import com.salesforce.bazel.eclipse.model.BazelPackageInfo;
import com.salesforce.bazel.eclipse.model.BazelPackageLocation;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceFactory;

/**
 * Factory for creating test environments for Eclipse functional tests. Produces a Mock Eclipse workspace from templates.
 * 
 * @author plaird
 */
public class EclipseFunctionalTestEnvironmentFactory {

    /**
     * Creates an environment with a Bazel workspace with Java packages on disk, but nothing has been imported yet into Eclipse. 
     * Includes:
     * <p><ul>
     * <li>A Bazel workspace on disk
     * <li>Bazel command runner prepared to run 'bazel info workspace' which is done during Bazel Import
     * <li>Everything else in base initialized state
     * </ul>
     */
    public static MockEclipse createMockEnvironment_PriorToImport_JavaPackages(File testTempDir, int numberOfJavaPackages,
            boolean explicitJavaTestDeps, boolean useAltConfigFileNames) throws Exception {
        // build out a Bazel workspace with specified number of Java packages, and a couple of genrules packages just to test that they get ignored
        File wsDir = new File(testTempDir, MockEclipse.BAZEL_WORKSPACE_NAME);
        wsDir.mkdirs();
        File outputbaseDir = new File(testTempDir, "outputbase");
        outputbaseDir.mkdirs();
        
        // simulate flags from .bazelrc
        Map<String, String> commandOptions = new HashMap<>();
        if (explicitJavaTestDeps) {
            commandOptions.put("explicit_java_test_deps", "true");
        }
        
        TestBazelWorkspaceDescriptor descriptor = new TestBazelWorkspaceDescriptor(wsDir, outputbaseDir).javaPackages(numberOfJavaPackages).
                genrulePackages(2).options(commandOptions).useAltConfigFileNames(useAltConfigFileNames);
        TestBazelWorkspaceFactory bazelWorkspaceCreator = new TestBazelWorkspaceFactory(descriptor);
        bazelWorkspaceCreator.build();

        // create the mock Eclipse runtime
        MockEclipse mockEclipse = new MockEclipse(bazelWorkspaceCreator, testTempDir);

        return mockEclipse;
    }
    
    /**
     * Creates an environment with a Bazel workspace with Java packages on disk, and the Java packages have been imported
     * as Eclipse projects. 
     * Includes:
     * <p><ul>
     * <li>A Bazel workspace on disk
     * <li>Each Bazel Java package is an imported Eclipse Java project with Bazel nature
     * </ul>
     */
    public static MockEclipse createMockEnvironment_Imported_All_JavaPackages(File testTempDir, int numberOfJavaPackages, 
            boolean computeClasspaths, boolean explicitJavaTestDeps) throws Exception {
        // create base configuration, which includes the real bazel workspace on disk
        MockEclipse mockEclipse = createMockEnvironment_PriorToImport_JavaPackages(testTempDir, numberOfJavaPackages,
            explicitJavaTestDeps, false);
        mockEclipse.getMockCommandBuilder().addAspectJsonFileResponses(mockEclipse.getBazelWorkspaceCreator().workspaceDescriptor.aspectFileSets);


        // scan the bazel workspace filesystem to build the list of Java projects
        BazelProjectImportScanner scanner = new BazelProjectImportScanner();
        BazelPackageInfo workspaceRootProject = scanner.getProjects(mockEclipse.getBazelWorkspaceRoot());
        
        // choose the list of Bazel packages to import, in this case we assume the user selected all Java packages
        List<BazelPackageLocation> bazelPackagesToImport = new ArrayList<>();
        bazelPackagesToImport.add(workspaceRootProject);
        addBazelPackageInfosToSelectedList(workspaceRootProject, bazelPackagesToImport);
                
        // run the import process (this is actually done in BazelImportWizard.performFinish() when a user is running the show)
        List<IProject> importedProjectsList = BazelEclipseProjectFactory.importWorkspace(workspaceRootProject, bazelPackagesToImport, 
            new MockImportOrderResolver(), new EclipseWorkProgressMonitor(), null);
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
    
    private static void addBazelPackageInfosToSelectedList(BazelPackageInfo currentNode, List<BazelPackageLocation> bazelPackagesToImport) {
        Collection<BazelPackageInfo> children = currentNode.getChildPackageInfos();
        for (BazelPackageInfo child : children) {
            // eventually this method should accept filter criteria, but for now we are just importing all packages
            bazelPackagesToImport.add(child);
        }
    }
}
