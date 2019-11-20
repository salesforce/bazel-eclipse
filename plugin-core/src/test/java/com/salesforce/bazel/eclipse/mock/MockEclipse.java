package com.salesforce.bazel.eclipse.mock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.preferences.BazelPreferencePage;
import com.salesforce.bazel.eclipse.runtime.ResourceHelper;
import com.salesforce.bazel.eclipse.test.TestBazelWorkspaceCreator;

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
    private File bazelExecutable;
    private TestBazelWorkspaceCreator bazelWorkspaceCreator;
    
    private MockIProjectFactory mockIProjectFactory;
    private MockIEclipsePreferences mockPrefs;
    private MockIPreferenceStore mockPrefsStore;
    private MockBazelAspectLocation mockBazelAspectLocation;
    private MockCommandBuilder mockCommandBuilder;
    private MockCommandConsole mockCommandConsole;
    private ResourceHelper mockResourceHelper;
    private MockJavaCoreHelper mockJavaCoreHelper;
    
    // if this is a full functional test, we will import the Bazel workspace which will result in 
    // a list of imported IProjects, which is kept here 
    private List<IProject> importedProjectsList = new ArrayList<>();
    
    public MockEclipse(File testTempDir) throws Exception {
        this.eclipseWorkspaceRoot = new File(testTempDir, "eclipse-workspace");
        this.eclipseWorkspaceRoot.mkdir();
        this.bazelWorkspaceRoot = new File(testTempDir, BAZEL_WORKSPACE_NAME);
        this.bazelWorkspaceRoot.mkdir();
        this.bazelOutputBase = new File(testTempDir, "bazel-output-base");
        this.bazelOutputBase.mkdir();
        File execroot_parent = new File(bazelOutputBase, "execroot");
        execroot_parent.mkdir();
        this.bazelExecutionRoot = new File(execroot_parent, "mock_workspace");
        this.bazelExecutionRoot.mkdir();
        File bazelbin_parent = new File(this.bazelExecutionRoot, "bazel-out");
        bazelbin_parent.mkdir();
        File bazelbin_os_parent = new File(bazelbin_parent, "darwin-fastbuild"); // eventually want to mock other platforms here
        bazelbin_os_parent.mkdir();
        this.bazelBin = new File(bazelbin_os_parent, "bin");
        this.bazelBin.mkdir();

        this.mockResourceHelper = new MockResourceHelper(eclipseWorkspaceRoot, this);
        this.mockPrefs = new MockIEclipsePreferences();
        this.mockPrefsStore = new MockIPreferenceStore();
        this.mockBazelAspectLocation = new MockBazelAspectLocation(this.bazelWorkspaceRoot);
        this.mockCommandConsole = new MockCommandConsole();
        this.mockCommandBuilder = new MockCommandBuilder(mockCommandConsole, bazelWorkspaceRoot, bazelOutputBase, bazelExecutionRoot, bazelBin);
        this.mockIProjectFactory = new MockIProjectFactory();
        this.mockJavaCoreHelper = new MockJavaCoreHelper();
        
        // create the fake executable bazel in the bazel workspace //tools/bazel
        File bazelExecDir = new File(this.bazelWorkspaceRoot, "tools");
        bazelExecDir.mkdir();
        this.bazelExecutable = new File(bazelExecDir, "bazel");
        this.bazelExecutable.createNewFile();
        this.bazelExecutable.setExecutable(true);
        this.mockPrefsStore.strings.put( BazelPreferencePage.BAZEL_PATH_PREF_NAME, this.bazelExecutable.getAbsolutePath());

        // initialize our plugins/feature with all the mock infrastructure
        // this simulates how our feature starts up when run inside of Eclipse
        BazelPluginActivator activator = new BazelPluginActivator();
        activator.startInternal(mockBazelAspectLocation, mockCommandBuilder, mockCommandConsole, 
            mockResourceHelper, mockJavaCoreHelper);
    }
    
    
    // GETTERS

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
        return this.bazelExecutable;
    }

    // Mock Objects
    
    public MockJavaCoreHelper getJavaCoreHelper() {
        return this.mockJavaCoreHelper;
    }
    
    public MockIEclipsePreferences getMockPrefs() {
        return this.mockPrefs;
    }

    public MockIPreferenceStore getMockPrefsStore() {
        return this.mockPrefsStore;
    }

    public MockBazelAspectLocation getMockBazelAspectLocation() {
        return this.mockBazelAspectLocation;
    }

    public MockCommandConsole getMockCommandConsole() {
        return this.mockCommandConsole;
    }
    
    public MockCommandBuilder getMockCommandBuilder() {
        return this.mockCommandBuilder;
    }
    
    public MockIProjectFactory getMockIProjectFactory() {
        return this.mockIProjectFactory;
    }
    
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

    public TestBazelWorkspaceCreator getBazelWorkspaceCreator() {
        return this.bazelWorkspaceCreator;
    }
    
    public void setBazelWorkspaceCreator(TestBazelWorkspaceCreator creator) {
        this.bazelWorkspaceCreator = creator;
    }
}
