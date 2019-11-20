package com.salesforce.bazel.eclipse.runtime;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.BazelPluginActivator;

/**
 * Resource helper implementation used when running in a live Eclipse runtime.
 * @author plaird
 *
 */
public class EclipseResourceHelper implements ResourceHelper {

    /**
     * Returns the IProject reference for the named project.
     */
    @Override
    public IProject getProjectByName(String projectName) {
        return getEclipseWorkspaceRoot().getProject(projectName);
    }
    /**
     * Creates the project described by newProject, with the passed description.
     */
    public IProject createProject(IProject newProject, IProjectDescription description, IProgressMonitor monitor) 
            throws CoreException {
        if (newProject == null) {
            throw new IllegalArgumentException("EclipseResourceHelper.create() was passed a null project.");
        }
        newProject.create(description, monitor);
        
        return newProject;
    }
    
    /**
     * Opens the project, or no-op if already open
     */
    public void openProject(IProject project, IProgressMonitor monitor) throws CoreException {
        project.open(monitor);
    }

    @Override
    public Preferences getProjectBazelPreferences(IProject project) {
        IScopeContext eclipseProjectScope = new ProjectScope(project);
        Preferences eclipseProjectPrefs = eclipseProjectScope.getNode(BazelPluginActivator.PLUGIN_ID);
        
        if (eclipseProjectPrefs == null) {
            BazelPluginActivator.error("Could not find the Preferences node for the Bazel plugin for project ["+project.getName()+"]");
        }
        
        return eclipseProjectPrefs;
    }

    /**
     * Gets the workspace node of this Eclipse workspace.
     */
    @Override
    public IWorkspace getEclipseWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    /**
     * Gets the root workspace node of this Eclipse workspace.
     */
    @Override
    public IWorkspaceRoot getEclipseWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }
    
    @Override
    public String getResourceAbsolutePath(IResource resource) {
        if (resource == null) {
            return null;
        }
        IPath projectLocation = resource.getLocation();
        String absProjectRoot = projectLocation.toOSString();
    
        return absProjectRoot;
    }
    
    @Override
    public IResource findMemberInWorkspace(IWorkspaceRoot workspaceRoot, IPath path) {
        IResource resource = workspaceRoot.findMember(path);        
        //System.out.println("findMemberInWorkspace: path="+path.toOSString()+" member.location="+getResourceAbsolutePath(resource));
        return resource;
    }
    
    @Override
    public IPreferenceStore getPreferenceStore(BazelPluginActivator activator) {
        return activator.getPreferenceStore();
    }
    
    @Override
    public IProjectDescription createProjectDescription(IProject project) {
        return ResourcesPlugin.getWorkspace().newProjectDescription(project.getName());
    }
    
    @Override
    public IProjectDescription getProjectDescription(IProject project) {
        try {
            return project.getDescription();
        } catch (CoreException ce) {
            throw new IllegalStateException(ce);
        }
    }
    
    @Override
    public void setProjectDescription(IProject project, IProjectDescription description) {
        try {
            project.setDescription(description, null);
        } catch (CoreException ce) {
            throw new IllegalStateException(ce);
        }
    }
    
    @Override
    public IScopeContext getProjectScopeContext(IProject project) {
        return new ProjectScope(project);
    }
    
    @Override
    public IFile getProjectFile(IProject project, String filename) {
        if (project == null) {
            return null;
        }
        return project.getFile(filename);
    }
    
    @Override
    public void createFileLink(IFile thisFile, IPath bazelWorkspaceLocation, int updateFlags, IProgressMonitor monitor) {
        //System.out.println("createFileLink: thisFile="+thisFile.getLocation().toOSString()+" bazelWorkspaceLocation="+bazelWorkspaceLocation.toOSString());
        
        try {
            thisFile.createLink(bazelWorkspaceLocation, updateFlags, monitor);
        } catch (Exception anyE) {
            throw new IllegalArgumentException(anyE);
        }
    }

    @Override
    public void createFolderLink(IFolder thisFolder, IPath bazelWorkspaceLocation, int updateFlags, IProgressMonitor monitor) {
        //System.out.println("createFolderLink: thisFolder="+thisFolder.getLocation().toOSString()+" bazelWorkspaceLocation="+bazelWorkspaceLocation.toOSString());
        
        try {
            thisFolder.createLink(bazelWorkspaceLocation, updateFlags, monitor);
        } catch (Exception anyE) {
            throw new IllegalArgumentException(anyE);
        }
    }
}
