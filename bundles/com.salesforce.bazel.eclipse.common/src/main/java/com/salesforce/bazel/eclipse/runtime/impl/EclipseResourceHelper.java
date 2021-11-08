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
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.activator.Activator;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

public class EclipseResourceHelper implements ResourceHelper {

    /**
     * Returns the IProject reference for the named project.
     */
    @Override
    public IProject getProjectByName(String projectName) {
        return getEclipseWorkspaceRoot().getProject(projectName);
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
     * Returns the IProjects for the Bazel Workspace project.
     */
    @Override
    public IProject[] getProjectsForBazelWorkspace(BazelWorkspace bazelWorkspace) {
        // todo once we support multiple Bazel workspaces, this will need to figure that out
        return getEclipseWorkspaceRoot().getProjects();
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

    /**
     * Deletes the specified project.
     */
    @Override
    //TODO implement method
    public void deleteProject(IProject project, IProgressMonitor monitor) throws CoreException {
        //        if (!isBazelRootProject(project)) {
        //            // clear the cached data for this project
        //            BazelWorkspace bzlWs = BazelJdtPlugin.getBazelWorkspace();
        //            BazelCommandManager bzlCmdMgr = BazelJdtPlugin.getBazelCommandManager();
        //            BazelWorkspaceCommandRunner bzlWsCmdRunner = bzlCmdMgr.getWorkspaceCommandRunner(bzlWs);
        //            projectManager.flushCaches(project.getName(), bzlWsCmdRunner);
        //        }
        //        boolean deleteContent = true; // delete metadata also, under the Eclipse Workspace directory
        //        boolean force = true;
        //        project.getProject().delete(deleteContent, force, monitor);
    }

    /**
     * Opens the project, or no-op if already open
     */
    @Override
    public void openProject(IProject project, IProgressMonitor monitor) throws CoreException {
        project.open(monitor);
    }

    @Override
    public Preferences getProjectBazelPreferences(IProject project) {
        IScopeContext eclipseProjectScope = new ProjectScope(project);
        Preferences eclipseProjectPrefs = eclipseProjectScope.getNode(Activator.PLUGIN_ID);

        if (eclipseProjectPrefs == null) {
            Activator.getDefault().logInfo(String.format(
                "Could not find the Preferences node for the Bazel plugin for project [%s]", project.getName()));
        }

        return eclipseProjectPrefs;
    }

    @Override
    public boolean isBazelRootProject(IProject project) {
        // fix to not be based on string comparison?
        return project.getName().startsWith(BazelNature.BAZELWORKSPACE_PROJECT_BASENAME);
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
    public IResource findMemberInWorkspace(IPath path) {
        IResource resource = getEclipseWorkspaceRoot().findMember(path);
        Activator.getDefault().logInfo(String.format("findMemberInWorkspace: path=%s member.location= %s",
            path.toOSString(), getResourceAbsolutePath(resource)));
        return resource;
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

    private static class DeferredProjectDescriptionUpdate {
        public IProject project;
        public IProjectDescription description;

        public DeferredProjectDescriptionUpdate(IProject project, IProjectDescription description) {
            this.project = project;
            this.description = description;
        }
    }

    private List<DeferredProjectDescriptionUpdate> deferredProjectDescriptionUpdates = new ArrayList<>();

    @Override
    public boolean setProjectDescription(IProject project, IProjectDescription description) {
        boolean needsDeferredApplication = false;
        try {
            project.setDescription(description, null);
        } catch (Exception ex) {
            // this is likely an issue with the resource tree being locked, so we have to defer this update
            // but it works for any type of issue
            Activator.getDefault().logInfo(
                String.format("Deferring updates to project [%s] because workspace is locked.", project.getName()));
            deferredProjectDescriptionUpdates.add(new DeferredProjectDescriptionUpdate(project, description));
            needsDeferredApplication = true;
        }
        return needsDeferredApplication;
    }

    /**
     * When setProjectDescription() fails, it is likely because the resource tree is locked. Call this method outside of
     * a locked code path if setProjectDescription() returned true.
     */
    @Override
    public void applyDeferredProjectDescriptionUpdates() {
        if (deferredProjectDescriptionUpdates.size() == 0) {
            return;
        }
        List<DeferredProjectDescriptionUpdate> updates = deferredProjectDescriptionUpdates;
        deferredProjectDescriptionUpdates = new ArrayList<>();
        for (DeferredProjectDescriptionUpdate update : updates) {
            // this could theoretically still fail, but we don't want an infinite retry so let this go if it fails
            setProjectDescription(update.project, update.description);
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
    public void createFileLink(IFile thisFile, IPath bazelWorkspaceLocation, int updateFlags,
            IProgressMonitor monitor) {
        try {
            Activator.getDefault().logInfo(String.format("createFileLink: thisFile=%s bazelWorkspaceLocation=%s",
                thisFile.getLocation().toOSString(), bazelWorkspaceLocation.toOSString()));
            thisFile.createLink(bazelWorkspaceLocation, updateFlags, monitor);
        } catch (Exception anyE) {
            throw new IllegalArgumentException(anyE);
        }
    }

    @Override
    public void createFolderLink(IFolder thisFolder, IPath bazelWorkspaceLocation, int updateFlags,
            IProgressMonitor monitor) {
        try {
            Activator.getDefault().logInfo(String.format("createFolderLink: thisFolder=%s bazelWorkspaceLocation=%s",
                thisFolder.getLocation().toOSString(), bazelWorkspaceLocation.toOSString()));
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
