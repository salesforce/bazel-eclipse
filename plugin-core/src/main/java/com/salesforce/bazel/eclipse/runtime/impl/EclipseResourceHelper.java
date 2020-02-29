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
package com.salesforce.bazel.eclipse.runtime.impl;

import java.io.File;

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
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jface.preference.IPreferenceStore;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.config.BazelProjectConstants;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;

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
    
    @Override
    public boolean isBazelRootProject(IProject project) {
        try {
            // fix to not be based on string comparison?
            return project.getDescription().getName().startsWith(BazelProjectConstants.BAZELWORKSPACE_PROJECT_BASENAME);
        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
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
    @Override
    public Process exec(String[] cmdLine, File workingDirectory) throws CoreException {
        return DebugPlugin.exec(cmdLine, workingDirectory);
    }
    
    @Override
    public IProcess newProcess(ILaunch launch, Process process, String label) {
        return DebugPlugin.newProcess(launch, process, label);
    }
}
