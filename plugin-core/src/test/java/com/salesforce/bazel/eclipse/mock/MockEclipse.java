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
package com.salesforce.bazel.eclipse.mock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.command.test.MockBazelAspectLocation;
import com.salesforce.bazel.eclipse.command.test.MockCommandBuilder;
import com.salesforce.bazel.eclipse.command.test.MockCommandConsole;
import com.salesforce.bazel.eclipse.command.test.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationDelegate;
import com.salesforce.bazel.eclipse.model.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.eclipse.preferences.BazelPreferencePage;
import com.salesforce.bazel.eclipse.workspace.test.TestBazelWorkspaceFactory;


/**
 * Primary entry point into the mocking framework for the core plugin. We only mock the small slice of Eclipse
 * that is used by our plugins. But it is enough to unit test the Core plugin which is the where we put all of our
 * code that integrates with Eclipse APIs.
 * 
 * @author plaird
 */
public class MockEclipse {

    public static final String BAZEL_WORKSPACE_NAME = "bazel-workspace";
    
    private File eclipseWorkspaceRoot;
    private File bazelWorkspaceRoot;
    private File bazelOutputBase;
    private File bazelExecutionRoot;
    private File bazelBin;

    // Eclipse mocking layer
    private MockIProjectFactory mockIProjectFactory;
    private MockIEclipsePreferences mockPrefs;
    private MockIPreferenceStore mockPrefsStore;
    private MockResourceHelper mockResourceHelper;
    private MockJavaCoreHelper mockJavaCoreHelper;
    private OperatingEnvironmentDetectionStrategy mockOsEnvStrategy = new MockOperatingEnvironmentDetectionStrategy();
    
    // Feature collaborators
    private BazelPluginActivator pluginActivator;
    private BazelLaunchConfigurationDelegate launchDelegate;

    // Bazel/filesystem layer (some mocks, some real filesystem artifacts)
    private TestBazelWorkspaceFactory bazelWorkspaceFactory;
    private TestBazelCommandEnvironmentFactory bazelCommandEnvironment;

    // if this is a full functional test, we will import the Bazel workspace which will result in 
    // a list of imported IProjects, which is kept here 
    private List<IProject> importedProjectsList = new ArrayList<>();
    
    /**
     * Create a MockEclipse environment with a richer Bazel workspace. First, the caller will create a 
     * Bazel workspace on disk with the TestBazelWorkspaceFactory harness. Typically it will be created with
     * some Java packages and maybe some genrule packages. 
     * <p>
     * Note that after this method is complete, the MockEclipse object is configured but the import step has
     * not been run so there will be no Eclipse projects created. See EclipseFunctionalTestEnvironmentFactory
     * for convenience methods for setting up a Bazel workspace, MockEclipse, and then import of the Bazel packages. 
     */
    public MockEclipse(TestBazelWorkspaceFactory bazelWorkspace, File testTempDir) throws Exception {
        this.bazelCommandEnvironment = new TestBazelCommandEnvironmentFactory();
        this.bazelCommandEnvironment.createTestEnvironment(bazelWorkspace, testTempDir, bazelWorkspace.workspaceDescriptor.testOptions);
        
        setup(bazelWorkspace, testTempDir);
    }
    
    private void setup(TestBazelWorkspaceFactory bazelWorkspaceFactory, File testTempDir) throws Exception {
        this.bazelWorkspaceFactory = bazelWorkspaceFactory;
        this.bazelWorkspaceRoot = bazelWorkspaceFactory.workspaceDescriptor.workspaceRootDirectory;
        this.bazelOutputBase = bazelWorkspaceFactory.workspaceDescriptor.outputBaseDirectory;
        this.bazelExecutionRoot = bazelWorkspaceFactory.workspaceDescriptor.dirExecRoot;
        this.bazelBin = bazelWorkspaceFactory.workspaceDescriptor.dirBazelBin;

        this.eclipseWorkspaceRoot = new File(testTempDir, "eclipse-workspace");
        this.eclipseWorkspaceRoot.mkdir();
        
        this.mockResourceHelper = new MockResourceHelper(eclipseWorkspaceRoot, this);
        this.mockPrefs = new MockIEclipsePreferences();
        this.mockPrefsStore = new MockIPreferenceStore();
        this.mockIProjectFactory = new MockIProjectFactory();
        this.mockJavaCoreHelper = new MockJavaCoreHelper();
        this.mockPrefsStore.strings.put( BazelPreferencePage.BAZEL_PATH_PREF_NAME, 
            this.bazelCommandEnvironment.bazelExecutable.getAbsolutePath());

        // feature collaborators
        this.pluginActivator = new BazelPluginActivator();
        this.launchDelegate = new BazelLaunchConfigurationDelegate();

        // initialize our plugins/feature with all the mock infrastructure
        // this simulates how our feature starts up when run inside of Eclipse
        this.pluginActivator.startInternal(this.bazelCommandEnvironment.bazelAspectLocation, 
            this.bazelCommandEnvironment.commandBuilder, this.bazelCommandEnvironment.commandConsole, 
            mockResourceHelper, mockJavaCoreHelper, mockOsEnvStrategy);
        
        // At this point our plugins are wired up, the Bazel workspace is created, but the user
        // has not run a Bazel Import... wizard yet. See EclipseFunctionalTestEnvironmentFactory
        // for how to run import.
    }
    
    
    // GETTERS

    // File system provisioning
    
    public TestBazelWorkspaceFactory getBazelWorkspaceCreator() {
        return this.bazelWorkspaceFactory;
    }
    
    public void setBazelWorkspaceCreator(TestBazelWorkspaceFactory creator) {
        this.bazelWorkspaceFactory = creator;
    }
    
    public TestBazelCommandEnvironmentFactory getBazelCommandEnvironmentFactory() {
        return this.bazelCommandEnvironment;
    }

    // File system
    
    public File getEclipseWorkspaceRoot() {
        return this.eclipseWorkspaceRoot;
    }

    public File getBazelWorkspaceRoot() {
        return this.bazelWorkspaceRoot;
    }

    public File getBazelOutputBase() {
        return this.bazelOutputBase;
    }

    public File getBazelExecutionRoot() {
        return this.bazelExecutionRoot;
    }

    public File getBazelBin() {
        return this.bazelBin;
    }

    public File getBazelExecutable() {
        return this.bazelCommandEnvironment.bazelExecutable.bazelExecutableFile;
    }

    // Mock Objects
    
    public MockJavaCoreHelper getMockJavaCoreHelper() {
        return this.mockJavaCoreHelper;
    }
    
    public MockResourceHelper getMockResourceHelper() {
        return this.mockResourceHelper;
    }
    
    public MockIEclipsePreferences getMockPrefs() {
        return this.mockPrefs;
    }

    public MockIPreferenceStore getMockPrefsStore() {
        return this.mockPrefsStore;
    }

    public MockBazelAspectLocation getMockBazelAspectLocation() {
        return this.bazelCommandEnvironment.bazelAspectLocation;
    }

    public MockCommandConsole getMockCommandConsole() {
        return this.bazelCommandEnvironment.commandConsole;
    }
    
    public MockCommandBuilder getMockCommandBuilder() {
        return this.bazelCommandEnvironment.commandBuilder;
    }
    
    public MockIProjectFactory getMockIProjectFactory() {
        return this.mockIProjectFactory;
    }
    
    
    // INTERNAL FEATURE COLLABORATORS
    
    public BazelPluginActivator getPluginActivator() {
        return this.pluginActivator;
    }
    
    public BazelLaunchConfigurationDelegate getLaunchDelegate() {
        return this.launchDelegate;
    }
    
    
    // INTERNAL STATE

    public List<IProject> getImportedProjectsList() {
        return this.importedProjectsList;
    }

    public IProject getImportedProject(String name) {
        for (IProject project : this.importedProjectsList) {
            String pName = project.getName();
            if (name.equals(pName)) {
                return project;
            }
        }
        return null;
    }
    
    public void setImportedProjectsList(List<IProject> importedProjectsList) {
        this.importedProjectsList = importedProjectsList;
    }

}
