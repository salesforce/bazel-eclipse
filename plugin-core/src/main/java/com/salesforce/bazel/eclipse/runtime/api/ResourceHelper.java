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
package com.salesforce.bazel.eclipse.runtime.api;

import java.io.File;

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
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.IProcessFactory;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jface.preference.IPreferenceStore;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.BazelPluginActivator;

/**
 * Interface for looking up Eclipse Workspace and Project resources, and subcomponents of each.
 * <p>
 * This is both a convenience layer and also a layer introduced to make mocking of the Eclipse environment
 * possible for functional tests. 
 * <p>
 * It is also a useful place to put breakpoints instead of debugging into Eclipse internals, and logging statements. 
 * Since these methods represent major integration points between the Bazel Eclipse Feature and the Eclipse SDK,
 * you can best observe the integration by instrumenting the implementation of this interface.
 */
public interface ResourceHelper {

    /**
     * Returns the IProject reference for the named project. 
     * <p>
     * If the project does not already exist, a new IProject is created and returned instead.
     * In this case the IProject.exists() method will return false. 
     */
    IProject getProjectByName(String projectName);

    /**
     * Creates a new project resource in the workspace using the given project
     * description. Upon successful completion, the project will exist but be closed.
     * <p>
     * Newly created projects have no session or persistent properties.
     * </p>
     * <p>
     * If the project content area given in the project description does not
     * contain a project description file, a project description file is written
     * in the project content area with the natures, build spec, comment, and
     * referenced projects as specified in the given project description.
     * If there is an existing project description file, it is not overwritten.
     * In either case, this method does <b>not</b> cause natures to be configured.
     * </p>
     * <p>
     * This method changes resources; these changes will be reported
     * in a subsequent resource change event, including an indication
     * that the project has been added to the workspace.
     * </p>
     * <p>
     * This method is long-running; progress and cancellation are provided
     * by the given progress monitor.
     * </p>
     *
     * @param description the project description
     * @param monitor a progress monitor, or <code>null</code> if progress
     *    reporting is not desired
     * @return the updated IProject
     */
    IProject createProject(IProject newProject, IProjectDescription description, IProgressMonitor monitor) 
            throws CoreException;
    
    /**
     * Opens this project.  No action is taken if the project is already open.
     * <p>
     * This is a convenience method, fully equivalent to
     * <code>open(IResource.NONE, monitor)</code>.
     * </p>
     * <p>
     * This method changes resources; these changes will be reported
     * in a subsequent resource change event that includes
     * an indication that the project has been opened and its resources
     * have been added to the tree.
     * </p>
     * <p>
     * This method is long-running; progress and cancellation are provided
     * by the given progress monitor.
     * </p>
     */
    void openProject(IProject project, IProgressMonitor monitor)
            throws CoreException;
    
    /**
     * Returns the absolute path in the local file system to this resource,
     * or <code>null</code> if no path can be determined. The IResource
     * is commonly an IProject in our usage.
     * <p>
     * If this resource is the workspace root, this method returns
     * the absolute local file system path of the platform working area.
     * </p><p>
     * If this resource is a project that exists in the workspace, this method
     * returns the path to the project's local content area. This is true regardless
     * of whether the project is open or closed. This value will be null in the case
     * where the location is relative to an undefined workspace path variable.
     * </p><p>
     * If this resource is a linked resource under a project that is open, this
     * method returns the resolved path to the linked resource's local contents.
     * This value will be null in the case where the location is relative to an
     * undefined workspace path variable.
     * </p><p>
     * If this resource is a file or folder under a project that exists, or a
     * linked resource under a closed project, this method returns a (non-
     * <code>null</code>) path computed from the location of the project's local
     * content area and the project- relative path of the file or folder. This is
     * true regardless of whether the file or folders exists, or whether the project
     * is open or closed. In the case of linked resources, the location of a linked resource
     * within a closed project is too computed from the location of the
     * project's local content area and the project-relative path of the resource. If the
     * linked resource resides in an open project then its location is computed
     * according to the link.
     * </p><p>
     * If this resource is a project that does not exist in the workspace,
     * or a file or folder below such a project, this method returns
     * <code>null</code>.  This method also returns <code>null</code> if called
     * on a resource that is not stored in the local file system.
     * </p>
     */
    String getResourceAbsolutePath(IResource resource);
    
    /**
     * Finds and returns the member resource identified by the given path in
     * this container, or <code>null</code> if no such resource exists.
     * The supplied path may be absolute or relative; in either case, it is
     * interpreted as relative to the workspace root resource. Trailing separators and the path's
     * device are ignored. If the path is empty the workspace root container is returned. Parent
     * references in the supplied path are discarded if they go above the workspace
     * root.
     * <p>
     * Note that no attempt is made to exclude team-private member resources
     * as with <code>members</code>.
     * </p><p>
     * N.B. Unlike the methods which traffic strictly in resource
     * handles, this method infers the resulting resource's type from the
     * resource existing at the calculated path in the workspace.
     * </p>
     *
     * @param path the path of the desired resource
     * @return the member resource, or <code>null</code> if no such
     *      resource exists
     */
    IResource findMemberInWorkspace(IWorkspaceRoot workspaceRoot, IPath path);
    
    /**
     * Gets the preferences object from the IProject
     */
    Preferences getProjectBazelPreferences(IProject project);
    
    /**
     * Gets the workspace node of this Eclipse workspace.
     */
    IWorkspace getEclipseWorkspace();

    /**
     * Gets the root workspace node of this Eclipse workspace.
     */
    IWorkspaceRoot getEclipseWorkspaceRoot();
    
    /**
     * Gets the preference store for the Core plugin.
     */
    IPreferenceStore getPreferenceStore(BazelPluginActivator activator);

    /**
     * Creates the project description object for the passed project. It is assumed
     * that this is called only during project import. Contains:
     * 1. the given project name 2. no references to other projects 3. an empty build spec 4. an empty comment
     */
    IProjectDescription createProjectDescription(IProject project);

    /**
     * Gets the project description object for the passed project.
     */
    IProjectDescription getProjectDescription(IProject project);
    
    /**
     * Sets the project description object for the passed project.
     */
    void setProjectDescription(IProject project, IProjectDescription description);
    
    /**
     * Retrieves the scope context for the project.
     */
    IScopeContext getProjectScopeContext(IProject project);

    /**
     * Gets a file from the project.
     */
    IFile getProjectFile(IProject project, String filename);
    
    /**
     * Creates a new file resource as a member of thisFile's parent resource.
     * The file's contents will be located in the file specified by the given
     * file system path.  The given path must be either an absolute file system
     * path, or a relative path whose first segment is the name of a workspace path
     * variable.
     * <p>
     * The {@link IResource#ALLOW_MISSING_LOCAL} update flag controls how this
     * method deals with cases where the local file system file to be linked does
     * not exist, or is relative to a workspace path variable that is not defined.
     * If {@link IResource#ALLOW_MISSING_LOCAL} is specified, the operation will succeed
     * even if the local file is missing, or the path is relative to an undefined
     * variable. If {@link IResource#ALLOW_MISSING_LOCAL} is not specified, the operation
     * will fail in the case where the local file system file does not exist or the
     * path is relative to an undefined variable.
     * </p>
     * <p>
     * The {@link IResource#REPLACE} update flag controls how this
     * method deals with cases where a resource of the same name as the
     * prospective link already exists. If {@link IResource#REPLACE}
     * is specified, then the existing linked resource's location is replaced
     * by localLocation's value.  This does <b>not</b>
     * cause the underlying file system contents of that resource to be deleted.
     * If {@link IResource#REPLACE} is not specified, this method will
     * fail if an existing resource exists of the same name.
     * </p>
     * <p>
     * The {@link IResource#HIDDEN} update flag indicates that this resource
     * should immediately be set as a hidden resource.  Specifying this flag
     * is equivalent to atomically calling {@link IResource#setHidden(boolean)}
     * with a value of <code>true</code> immediately after creating the resource.
     * </p>
     * <p>
     * Update flags other than those listed above are ignored.
     * </p>
     * <p>
     * This method synchronizes this resource with the local file system at the given
     * location.
     * </p>
     * <p>
     * This method changes resources; these changes will be reported
     * in a subsequent resource change event, including an indication
     * that the file has been added to its parent.
     * </p>
     * <p>
     * This method is long-running; progress and cancellation are provided
     * by the given progress monitor.
     * </p>
     */
    void createFileLink(IFile thisFile, IPath bazelWorkspaceLocation, int updateFlags, IProgressMonitor monitor);
    
    /**
     * Creates a new folder resource as a member of thisFolder's parent resource.
     * The folder's contents will be located in the directory specified by the given
     * file system path.  The given path must be either an absolute file system
     * path, or a relative path whose first segment is the name of a workspace path
     * variable.
     * <p>
     * The <code>ALLOW_MISSING_LOCAL</code> update flag controls how this
     * method deals with cases where the local file system directory to be linked does
     * not exist, or is relative to a workspace path variable that is not defined.
     * If <code>ALLOW_MISSING_LOCAL</code> is specified, the operation will succeed
     * even if the local directory is missing, or the path is relative to an
     * undefined variable. If <code>ALLOW_MISSING_LOCAL</code> is not specified, the
     * operation will fail in the case where the local file system directory does
     * not exist or the path is relative to an undefined variable.
     * </p>
     * <p>
     * The {@link IResource#REPLACE} update flag controls how this
     * method deals with cases where a resource of the same name as the
     * prospective link already exists. If {@link IResource#REPLACE}
     * is specified, then the existing linked resource's location is replaced
     * by localLocation's value.  This does <b>not</b>
     * cause the underlying file system contents of that resource to be deleted.
     * If {@link IResource#REPLACE} is not specified, this method will
     * fail if an existing resource exists of the same name.
     * </p>
     * <p>
     * The {@link IResource#BACKGROUND_REFRESH} update flag controls how
     * this method synchronizes the new resource with the filesystem. If this flag is
     * specified, resources on disk will be synchronized in the background after the
     * method returns. Child resources of the link may not be available until
     * this background refresh completes. If this flag is not specified, resources are
     * synchronized in the foreground before this method returns.
     * </p>
     * <p>
     * The {@link IResource#HIDDEN} update flag indicates that this resource
     * should immediately be set as a hidden resource.  Specifying this flag
     * is equivalent to atomically calling {@link IResource#setHidden(boolean)}
     * with a value of <code>true</code> immediately after creating the resource.
     * </p>
     * <p>
     * Update flags other than those listed above are ignored.
     * </p>
     * <p>
     * This method changes resources; these changes will be reported
     * in a subsequent resource change event, including an indication
     * that the folder has been added to its parent.
     * </p>
     * <p>
     * This method is long-running; progress and cancellation are provided
     * by the given progress monitor.
     * </p>
     */
    void createFolderLink(IFolder thisFolder, IPath bazelWorkspaceLocation, int updateFlags, IProgressMonitor monitor);
    
    /**
     * Convenience method that performs a runtime exec on the given command line
     * in the context of the specified working directory, and returns the
     * resulting process. If the current runtime does not support the
     * specification of a working directory, the status handler for error code
     * <code>ERR_WORKING_DIRECTORY_NOT_SUPPORTED</code> is queried to see if the
     * exec should be re-executed without specifying a working directory.
     * From DebugPlugin.
     *
     * @param cmdLine the command line
     * @param workingDirectory the working directory, or <code>null</code>
     * @return the resulting process or <code>null</code> if the exec is
     *  canceled
     * @exception CoreException if the exec fails
     * @see Runtime
     *
     * @since 2.1
     */
    public Process exec(String[] cmdLine, File workingDirectory) throws CoreException;
    
    /**
     * Creates and returns a new process representing the given
     * <code>java.lang.Process</code>. A streams proxy is created
     * for the I/O streams in the system process. The process
     * is added to the given launch.
     * From DebugPlugin.
     * <p>
     * If the launch configuration associated with the given launch
     * specifies a process factory, it will be used to instantiate
     * the new process.
     * </p>
     * @param launch the launch the process is contained in
     * @param process the system process to wrap
     * @param label the label assigned to the process
     * @return the process
     * @see IProcess
     * @see IProcessFactory
     */
    public IProcess newProcess(ILaunch launch, Process process, String label);
}