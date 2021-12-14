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
import com.salesforce.bazel.eclipse.config.EclipseBazelConfigurationManager;
import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationDelegate;
import com.salesforce.bazel.eclipse.preferences.BazelPreferenceKeys;
import com.salesforce.bazel.sdk.command.test.MockBazelAspectLocation;
import com.salesforce.bazel.sdk.command.test.MockCommandBuilder;
import com.salesforce.bazel.sdk.command.test.MockCommandConsole;
import com.salesforce.bazel.sdk.command.test.TestBazelCommandEnvironmentFactory;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;

/**
 * Primary entry point into the mocking framework for the core plugin. We only mock the small slice of Eclipse that is
 * used by our plugins. But it is enough to unit test the Core plugin which is the where we put all of our code that
 * integrates with Eclipse APIs.
 */
public class MockEclipse {

    // generic name for the bazel workspace directory, keep it short to avoid Windows path length issues
    public static final String BAZEL_WORKSPACE_NAME = "bazelws";

    private File eclipseWorkspaceRoot;
    private File bazelWorkspaceRoot;
    private File bazelOutputBase;
    private File bazelExecutionRoot;
    private File bazelBin;

    // Eclipse mocking layer
    private MockIProjectFactory mockIProjectFactory;
    //    private MockIPreferenceStore mockPrefsStore;
    private MockResourceHelper mockResourceHelper;
    private MockCorePreferencesStoreHelper mockCorePreferencesStoreHelper;
    private MockJavaCoreHelper mockJavaCoreHelper;
    private final OperatingEnvironmentDetectionStrategy mockOsEnvStrategy =
            new MockOperatingEnvironmentDetectionStrategy();

    private MockIEclipsePreferences mockPrefs;

    // Feature collaborators
    private BazelPluginActivator pluginActivator;
    private BazelLaunchConfigurationDelegate launchDelegate;
    private BazelProjectManager projectManager;
    private BazelConfigurationManager configManager;

    // Bazel/filesystem layer (some mocks, some real filesystem artifacts)
    private TestBazelWorkspaceFactory bazelWorkspaceFactory;
    private final TestBazelCommandEnvironmentFactory bazelCommandEnvironment;

    // if this is a full functional test, we will import the Bazel workspace which will result in
    // a list of imported IProjects, which is kept here
    private List<IProject> importedProjectsList = new ArrayList<>();

    /**
     * Create a MockEclipse environment with a richer Bazel workspace. First, the caller will create a Bazel workspace
     * on disk with the TestBazelWorkspaceFactory harness. Typically it will be created with some Java packages and
     * maybe some genrule packages.
     * <p>
     * Note that after this method is complete, the MockEclipse object is configured but the import step has not been
     * run so there will be no Eclipse projects created. See EclipseFunctionalTestEnvironmentFactory for convenience
     * methods for setting up a Bazel workspace, MockEclipse, and then import of the Bazel packages.
     */
    public MockEclipse(TestBazelWorkspaceFactory bazelWorkspace, File testTempDir) throws Exception {
        bazelCommandEnvironment = new TestBazelCommandEnvironmentFactory();
        bazelCommandEnvironment.createTestEnvironment(bazelWorkspace, testTempDir,
            bazelWorkspace.workspaceDescriptor.testOptions);

        setup(bazelWorkspace, testTempDir);
    }

    private void setup(TestBazelWorkspaceFactory bazelWorkspaceFactory, File testTempDir) throws Exception {
        this.bazelWorkspaceFactory = bazelWorkspaceFactory;
        bazelWorkspaceRoot = bazelWorkspaceFactory.workspaceDescriptor.workspaceRootDirectory;
        bazelOutputBase = bazelWorkspaceFactory.workspaceDescriptor.outputBaseDirectory;
        bazelExecutionRoot = bazelWorkspaceFactory.workspaceDescriptor.dirExecRoot;
        bazelBin = bazelWorkspaceFactory.workspaceDescriptor.dirBazelBin;

        eclipseWorkspaceRoot = new File(testTempDir, "eclipse-workspace");
        eclipseWorkspaceRoot.mkdir();

        mockResourceHelper = new MockResourceHelper(eclipseWorkspaceRoot, this);
        mockCorePreferencesStoreHelper = new MockCorePreferencesStoreHelper(this);
        mockPrefs = new MockIEclipsePreferences();
        //        mockPrefsStore = new MockIPreferenceStore();
        mockIProjectFactory = new MockIProjectFactory();
        mockJavaCoreHelper = new MockJavaCoreHelper();

        // Eclipse preferences for BEF
        setupDefaultPreferences();

        // feature collaborators
        pluginActivator = new BazelPluginActivator();
        launchDelegate = new BazelLaunchConfigurationDelegate();
        configManager = new EclipseBazelConfigurationManager(mockCorePreferencesStoreHelper);
        projectManager = new MockBazelProjectManager(mockResourceHelper, mockJavaCoreHelper);

        // initialize our plugins/feature with all the mock infrastructure
        // this simulates how our feature starts up when run inside of Eclipse
        pluginActivator.startInternal(new MockComponentContextInitializer(this),
            bazelCommandEnvironment.commandBuilder,
            bazelCommandEnvironment.commandConsole,
            mockJavaCoreHelper);

        // At this point our plugins are wired up, the Bazel workspace is created, but the user
        // has not run a Bazel Import... wizard yet. See EclipseFunctionalTestEnvironmentFactory
        // for how to run import.
    }

    private void setupDefaultPreferences() {
        mockPrefs.strings.put(BazelPreferenceKeys.BAZEL_PATH_PREF_NAME,
            bazelCommandEnvironment.bazelExecutable.getAbsolutePath());
        mockPrefs.booleans.put(BazelPreferenceKeys.PROJECTSTRUCTUREOPTIMIZATIONS_PREF_NAME, true);
    }

    // GETTERS

    // File system provisioning

    public TestBazelWorkspaceFactory getBazelWorkspaceCreator() {
        return bazelWorkspaceFactory;
    }

    public void setBazelWorkspaceCreator(TestBazelWorkspaceFactory creator) {
        bazelWorkspaceFactory = creator;
    }

    public TestBazelCommandEnvironmentFactory getBazelCommandEnvironmentFactory() {
        return bazelCommandEnvironment;
    }

    // File system

    public File getEclipseWorkspaceRoot() {
        return eclipseWorkspaceRoot;
    }

    public File getBazelWorkspaceRoot() {
        return bazelWorkspaceRoot;
    }

    public File getBazelOutputBase() {
        return bazelOutputBase;
    }

    public File getBazelExecutionRoot() {
        return bazelExecutionRoot;
    }

    public File getBazelBin() {
        return bazelBin;
    }

    public File getBazelExecutable() {
        return bazelCommandEnvironment.bazelExecutable.bazelExecutableFile;
    }

    // Mock Objects

    public OperatingEnvironmentDetectionStrategy getOsEnvStrategy() {
        return mockOsEnvStrategy;
    }

    public BazelProjectManager getProjectManager() {
        return projectManager;
    }

    public MockJavaCoreHelper getMockJavaCoreHelper() {
        return mockJavaCoreHelper;
    }

    public MockResourceHelper getMockResourceHelper() {
        return mockResourceHelper;
    }

    public MockIEclipsePreferences getMockPrefs() {
        return mockPrefs;
    }

    public MockBazelAspectLocation getMockBazelAspectLocation() {
        return bazelCommandEnvironment.bazelAspectLocation;
    }

    public MockCommandConsole getMockCommandConsole() {
        return bazelCommandEnvironment.commandConsole;
    }

    public MockCommandBuilder getMockCommandBuilder() {
        return bazelCommandEnvironment.commandBuilder;
    }

    public MockIProjectFactory getMockIProjectFactory() {
        return mockIProjectFactory;
    }

    public BazelConfigurationManager getConfigManager() {
        return configManager;
    }

    public MockCorePreferencesStoreHelper getMockCorePreferencesStoreHelper() {
        return mockCorePreferencesStoreHelper;
    }

    // INTERNAL FEATURE COLLABORATORS

    public BazelPluginActivator getPluginActivator() {
        return pluginActivator;
    }

    public BazelLaunchConfigurationDelegate getLaunchDelegate() {
        return launchDelegate;
    }

    // INTERNAL STATE

    public List<IProject> getImportedProjectsList() {
        return importedProjectsList;
    }

    public IProject getImportedProject(String name) {
        for (IProject project : importedProjectsList) {
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
