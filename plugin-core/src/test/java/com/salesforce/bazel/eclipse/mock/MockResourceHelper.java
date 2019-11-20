package com.salesforce.bazel.eclipse.mock;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.mockito.Mockito;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.runtime.ResourceHelper;

public class MockResourceHelper implements ResourceHelper {
    
    /**
     * List of mock projects that will be used during the test. Your test might add projects
     * that will be retrieved during the test if you are testing 'existing' projects use cases. 
     * The key is the project name, and the value is the mock project.
     * For projects that are being created new during the test, don't add the project for best results.
     */
    public Map<String, IProject> mockProjects = new TreeMap<>();
    
    /**
     * Preference objects that are kept for each project.
     * The key is the project name, and the value is the mock prefs object.
     */
    public Map<String, MockIEclipsePreferences> mockPrefs = new TreeMap<>();

    /**
     * Description objects that are kept for each project.
     * The key is the project name, and the value is the mock prefs object.
     */
    public Map<String, IProjectDescription> mockDescriptions = new TreeMap<>();
    
    /**
     * Scope objects that are kept for each project.
     * The key is the project name, and the value is the mock prefs object.
     */
    public Map<String, IScopeContext> mockScopeContexts = new TreeMap<>();
    
    private MockIWorkspace workspace = null;
    private MockIWorkspaceRoot workspaceRoot = null;
    private File eclipseWorkspaceDir;
    private MockEclipse mockEclipse;
    
    public MockResourceHelper(File eclipseWorkspaceDir, MockEclipse mockEclipse) {
        this.eclipseWorkspaceDir = eclipseWorkspaceDir;
        this.mockEclipse = mockEclipse;
    }
    
    @Override
    public IProject getProjectByName(String projectName) {
        IProject project = mockProjects.get(projectName);
        
        if (project == null) {
            project = new MockIProjectFactory().buildGenericIProject(projectName, this.eclipseWorkspaceDir.getAbsolutePath(),
                null);
        }
        
        return project;
    }
    
    @Override
    public IProject createProject(IProject newProject, IProjectDescription description, IProgressMonitor monitor)
            throws CoreException {

        Mockito.when(newProject.getDescription()).thenReturn(description);
        Mockito.when(newProject.exists()).thenReturn(true);
        
        return newProject;
    }

    @Override
    public void openProject(IProject project, IProgressMonitor monitor) throws CoreException {
        Mockito.when(project.isOpen()).thenReturn(true);
    }
    
    public IWorkspace getEclipseWorkspace() {
        if (workspace == null) {
            workspace = new MockIWorkspace(this);
        }
        return workspace;
    }

    public IWorkspaceRoot getEclipseWorkspaceRoot() {
        if (workspaceRoot == null) {
            workspaceRoot = new MockIWorkspaceRoot(mockEclipse, eclipseWorkspaceDir);
        }
        return workspaceRoot;
    }

    @Override
    public Preferences getProjectBazelPreferences(IProject project) {
        if (project == null) {
            throw new IllegalArgumentException();
        }
        MockIEclipsePreferences prefs = mockPrefs.get(project.getName());
        if (prefs == null) {
            prefs = new MockIEclipsePreferences();
            mockPrefs.put(project.getName(), prefs);
        }
        
        return prefs;
    }

    @Override
    public String getResourceAbsolutePath(IResource resource) {
        IPath projectLocation = resource.getLocation();
        String absProjectRoot = projectLocation.toOSString();
    
        return absProjectRoot;
    }
    
    @Override
    public IResource findMemberInWorkspace(IWorkspaceRoot workspaceRoot, IPath path) {
        return workspaceRoot.findMember(path);
    }

    @Override
    public IPreferenceStore getPreferenceStore(BazelPluginActivator activator) {
        return mockEclipse.getMockPrefsStore();
    }

    @Override
    public IProjectDescription createProjectDescription(IProject project) {
        if (mockDescriptions.containsKey(project.getName())) {
            System.err.println("Bazel Eclipse Feature is creating a new description for a project, but the project already has one.");
        }
        
        IProjectDescription description = new MockIProjectDescription();
        mockDescriptions.put(project.getName(), description);
        return description;
    }
    
    @Override
    public IProjectDescription getProjectDescription(IProject project) {
        IProjectDescription description = mockDescriptions.get(project.getName());
        if (description == null) {
            // check if the description has been added as a thenReturn to the project
            try {
                description = project.getDescription();
            } catch (Exception anyE) {}
            
            // the description is not available, create it
            if (description == null) {
                description = new MockIProjectDescription();
            }
            mockDescriptions.put(project.getName(), description);
        }
        return description;
    }

    @Override
    public void setProjectDescription(IProject project, IProjectDescription description) {
        mockDescriptions.put(project.getName(), description);
    }

    @Override
    public IScopeContext getProjectScopeContext(IProject project) {
        IScopeContext scope = mockScopeContexts.get(project.getName());
        if (scope == null) {
            scope = new MockIScopeContext(project);
            mockScopeContexts.put(project.getName(), scope);
        }
        return scope;
    }

    @Override
    public IFile getProjectFile(IProject project, String filename) {
        IFile file = MockIFileFactory.createMockIFile(false, project, new Path(filename));
        
        return file;
    }

    @Override
    public void createFileLink(IFile thisFile, IPath bazelWorkspaceLocation, int updateFlags, IProgressMonitor monitor) {
        if (thisFile == null) {
            throw new IllegalArgumentException("createFileLink with a null 'thisFile'");
        }
        if (thisFile.getLocation() == null) {
            throw new IllegalArgumentException("createFileLink with a null location for 'thisFile'");
        }
        if (bazelWorkspaceLocation == null) {
            throw new IllegalArgumentException("createFileLink with a null 'bazelWorkspaceLocation'");
        }
        //System.out.println("createFileLink: thisFile="+thisFile.getLocation().toOSString()+" bazelWorkspaceLocation="+bazelWorkspaceLocation.toOSString());

        // create the target to the actual location on disk
        IProject project = thisFile.getProject();
        IFile targetFile = MockIFileFactory.createMockIFile(true, project, bazelWorkspaceLocation);
        
        // MockIWorkspaceRoot is where the link bookkeeping is stored, as links are usually resolved with IWorkspaceRoot.findMember() calls.
        workspaceRoot.linkedFiles.put(thisFile.getLocation().makeAbsolute().toOSString(), targetFile);
    }

    @Override
    public void createFolderLink(IFolder thisFolder, IPath bazelWorkspaceLocation, int updateFlags, IProgressMonitor monitor) {
        if (thisFolder == null) {
            throw new IllegalArgumentException("createFolderLink with a null 'thisFolder'");
        }
        if (bazelWorkspaceLocation == null) {
            throw new IllegalArgumentException("createFolderLink with a null 'bazelWorkspaceLocation'");
        }
        //System.out.println("createFolderLink: thisFolder="+thisFolder.getLocation().toOSString()+" bazelWorkspaceLocation="+bazelWorkspaceLocation.toOSString());

        // create the target to the actual location on disk
        IProject project = thisFolder.getProject();
        IFolder targetFile = new MockIFolder(project, bazelWorkspaceLocation);
        
        // MockIWorkspaceRoot is where the link bookkeeping is stored, as links are usually resolved with IWorkspaceRoot.findMember() calls.
        workspaceRoot.linkedFolders.put(thisFolder.getLocation().makeAbsolute().toOSString(), targetFile);
    }

}
