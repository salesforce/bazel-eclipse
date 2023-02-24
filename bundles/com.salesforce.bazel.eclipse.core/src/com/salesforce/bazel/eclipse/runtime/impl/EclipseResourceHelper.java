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
import java.util.ArrayList;
import java.util.List;

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
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.core.BazelCorePluginSharedContstants;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

public class EclipseResourceHelper implements ResourceHelper {
    private static class DeferredProjectDescriptionUpdate {
        public IProject project;
        public IProjectDescription description;

        public DeferredProjectDescriptionUpdate(IProject project, IProjectDescription description) {
            this.project = project;
            this.description = description;
        }
    }

    private static final LogHelper LOG = LogHelper.log(EclipseResourceHelper.class);

    private List<DeferredProjectDescriptionUpdate> deferredProjectDescriptionUpdates = new ArrayList<>();

    /**
     * When setProjectDescription() fails, it is likely because the resource tree is locked. Call this method outside of
     * a locked code path if setProjectDescription() returned true.
     */
    @Override
    public void applyDeferredProjectDescriptionUpdates() {
        if (deferredProjectDescriptionUpdates.size() == 0) {
            return;
        }
        var updates = deferredProjectDescriptionUpdates;
        deferredProjectDescriptionUpdates = new ArrayList<>();
        for (DeferredProjectDescriptionUpdate update : updates) {
            // this could theoretically still fail, but we don't want an infinite retry so let this go if it fails
            setProjectDescription(update.project, update.description);
        }
    }

    @Override
    public void createFileLink(IFile thisFile, IPath bazelWorkspaceLocation, int updateFlags,
            IProgressMonitor monitor) {
        try {
            LOG.info("createFileLink: thisFile={} bazelWorkspaceLocation={}", thisFile.getLocation().toOSString(),
                bazelWorkspaceLocation.toOSString());
            thisFile.createLink(bazelWorkspaceLocation, updateFlags, monitor);
        } catch (Exception anyE) {
            throw new IllegalArgumentException(anyE);
        }
    }

    @Override
    public void createFolderLink(IFolder thisFolder, IPath bazelWorkspaceLocation, int updateFlags,
            IProgressMonitor monitor) {
        try {
            LOG.info("createFolderLink: thisFolder={} bazelWorkspaceLocation={}", thisFolder.getLocation().toOSString(),
                bazelWorkspaceLocation.toOSString());
            thisFolder.createLink(bazelWorkspaceLocation, updateFlags, monitor);
        } catch (Exception anyE) {
            throw new IllegalArgumentException(anyE);
        }
    }

    /**
     * Creates the project described by newProject, with the passed description.
     */
    @Override
    public IProject createProject(IProject newProject, IProjectDescription description, IProgressMonitor monitor)
            throws CoreException {
        if (newProject == null) {
            throw new IllegalArgumentException("EclipseResourceHelper.create() was passed a null project.");
        }
        newProject.create(description, monitor);

        return newProject;
    }

    @Override
    public IProjectDescription createProjectDescription(IProject project) {
        return ResourcesPlugin.getWorkspace().newProjectDescription(project.getName());
    }

    /**
     * Deletes the specified project.
     */
    @Override
    public void deleteProject(IProject project, IProgressMonitor monitor) throws CoreException {
        if (!isBazelRootProject(project)) {
            // clear the cached data for this project
            var bzlWs = ComponentContext.getInstance().getBazelWorkspace();
            var bzlCmdMgr = ComponentContext.getInstance().getBazelCommandManager();
            var bzlWsCmdRunner = bzlCmdMgr.getWorkspaceCommandRunner(bzlWs);
            ComponentContext.getInstance().getProjectManager().flushCaches(project.getName(), bzlWsCmdRunner);
        }
        var deleteContent = true; // delete metadata also, under the Eclipse Workspace directory
        var force = true;
        project.getProject().delete(deleteContent, force, monitor);
    }

    @Override
    public Process exec(String[] cmdLine, File workingDirectory) throws CoreException {
        return DebugPlugin.exec(cmdLine, workingDirectory);
    }

    @Override
    public IResource findMemberInWorkspace(IPath path) {
        var resource = getEclipseWorkspaceRoot().findMember(path);
        LOG.info("findMemberInWorkspace: path={} member.location={}", path.toOSString(),
            getResourceAbsolutePath(resource));
        return resource;
    }

    /**
     * Returns the IProject reference for the Bazel Workspace project.
     */
    @Override
    public IProject getBazelWorkspaceProject(BazelWorkspace bazelWorkspace) {
        for (IProject candidate : getEclipseWorkspaceRoot().getProjects()) {
            if (isBazelRootProject(candidate)) {
                return candidate;
            }
        }
        return null;
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
    public Preferences getProjectBazelPreferences(IProject project) {
        IScopeContext eclipseProjectScope = new ProjectScope(project);
        Preferences eclipseProjectPrefs = eclipseProjectScope.getNode(BazelCorePluginSharedContstants.PLUGIN_ID);

        if (eclipseProjectPrefs == null) {
            LOG.info("Could not find the Preferences node for the Bazel plugin for project [{}]", project.getName());
        }

        try {
            eclipseProjectPrefs.sync();
        } catch (BackingStoreException bse) {
            LOG.info("Could not find the Preferences node for the Bazel plugin for project [{}]", project.getName());
        }

        return eclipseProjectPrefs;
    }

    /**
     * Returns the IProject reference for the named project.
     */
    @Override
    public IProject getProjectByName(String projectName) {
        return getEclipseWorkspaceRoot().getProject(projectName);
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
    public IFile getProjectFile(IProject project, String filename) {
        if (project == null) {
            return null;
        }
        return project.getFile(filename);
    }

    @Override
    public IScopeContext getProjectScopeContext(IProject project) {
        return new ProjectScope(project);
    }

    /**
     * Returns the IProjects for the Bazel Workspace project.
     */
    @Override
    public IProject[] getProjectsForBazelWorkspace(BazelWorkspace bazelWorkspace) {
        // todo once we support multiple Bazel workspaces, this will need to figure that out
        return getEclipseWorkspaceRoot().getProjects();
    }

    @Override
    public String getResourceAbsolutePath(IResource resource) {
        if (resource == null) {
            return null;
        }
        var projectLocation = resource.getLocation();
        return projectLocation.toOSString();
    }

    @Override
    public boolean isBazelRootProject(IProject project) {
        // fix to not be based on string comparison?
        return project.getName().startsWith(BazelNature.BAZELWORKSPACE_PROJECT_BASENAME);
    }

    @Override
    public IProcess newProcess(ILaunch launch, Process process, String label) {
        return DebugPlugin.newProcess(launch, process, label);
    }

    /**
     * Opens the project, or no-op if already open
     */
    @Override
    public void openProject(IProject project, IProgressMonitor monitor) throws CoreException {
        project.open(monitor);
    }

    @Override
    public boolean setProjectDescription(IProject project, IProjectDescription description) {
        var needsDeferredApplication = false;
        try {
            project.setDescription(description, null);
        } catch (Exception ex) {
            // this is likely an issue with the resource tree being locked, so we have to defer this update
            // but it works for any type of issue
            LOG.info("Deferring updates to project [{}] because workspace is locked.", project.getName());
            deferredProjectDescriptionUpdates.add(new DeferredProjectDescriptionUpdate(project, description));
            needsDeferredApplication = true;
        }
        return needsDeferredApplication;
    }
}
