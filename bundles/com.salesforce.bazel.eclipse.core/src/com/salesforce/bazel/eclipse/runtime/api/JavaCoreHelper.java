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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Interface for working with Eclipse Java projects.
 * <p>
 * This is both a convenience layer and also a layer introduced to make mocking of the Eclipse environment possible for
 * functional tests.
 * <p>
 * It is also a useful place to put breakpoints instead of debugging into Eclipse internals, and logging statements.
 * Since these methods represent major integration points between the Bazel Eclipse Feature and the Eclipse SDK, you can
 * best observe the integration by instrumenting the implementation of this interface.
 *
 * @deprecated such mocking is not needed - we should do proper Eclipse integration tests with SWT Bot and full
 *             OSGi/Eclipse environment
 */
@Deprecated
public interface JavaCoreHelper {

    /**
     * Gets the list of all Bazel Java projects in the Workspace.
     *
     * @param includeRootProject
     *            if true, the project representing the Bazel WORKSPACE root is also included
     */
    IJavaProject[] getAllBazelJavaProjects(boolean includeBazelWorkspaceRootProject);

    /**
     * Returns the Java model.
     *
     * @param root
     *            the given root
     * @return the Java model, or <code>null</code> if the root is null
     */
    IJavaModel getJavaModelForWorkspace(IWorkspaceRoot root);

    /**
     * Returns the Java project corresponding to the given project.
     * <p>
     * Creating a Java Project has the side effect of creating and opening all of the project's parents if they are not
     * yet open.
     * </p>
     * <p>
     * Note that no check is done at this time on the existence or the java nature of this project.
     * </p>
     *
     * @param project
     *            the given project
     * @return the Java project corresponding to the given project, null if the given project is null
     */
    IJavaProject getJavaProjectForProject(IProject project);

    /**
     * Returns the raw classpath for the project, as a list of classpath entries. This corresponds to the exact set of
     * entries which were assigned using <code>setRawClasspath</code>, in particular such a classpath may contain
     * classpath variable and classpath container entries. Classpath variable and classpath container entries can be
     * resolved using the helper method <code>getResolvedClasspath</code>; classpath variable entries also can be
     * resolved individually using <code>JavaCore#getClasspathVariable</code>).
     * <p>
     * Both classpath containers and classpath variables provides a level of indirection that can make the
     * <code>.classpath</code> file stable across workspaces. As an example, classpath variables allow a classpath to no
     * longer refer directly to external JARs located in some user specific location. The classpath can simply refer to
     * some variables defining the proper locations of these external JARs. Similarly, classpath containers allows
     * classpath entries to be computed dynamically by the plug-in that defines that kind of classpath container.
     * </p>
     * <p>
     * Note that in case the project isn't yet opened, the classpath will be read directly from the associated
     * <code>.classpath</code> file.
     * </p>
     *
     * @return the raw classpath for the project, as a list of classpath entries
     */
    IClasspathEntry[] getRawClasspath(IJavaProject javaProject);

    /**
     * This is a helper method returning the resolved classpath for the project as a list of simple (non-variable,
     * non-container) classpath entries. All classpath variable and classpath container entries in the project's raw
     * classpath will be replaced by the simple classpath entries they resolve to.
     * <p>
     * The resulting resolved classpath is accurate for the given point in time. If the project's raw classpath is later
     * modified, or if classpath variables are changed, the resolved classpath can become out of date. Because of this,
     * hanging on resolved classpath is not recommended.
     * </p>
     * <p>
     * Note that if the resolution creates duplicate entries (i.e. {@link IClasspathEntry entries} which are
     * {@link Object#equals(Object)}), only the first one is added to the resolved classpath.
     * </p>
     *
     * @param ignoreUnresolvedEntry
     *            indicates how to handle unresolvable variables and containers; <code>true</code> indicates that
     *            missing variables and unresolvable classpath containers should be silently ignored, and that the
     *            resulting list should consist only of the entries that could be successfully resolved;
     *            <code>false</code> indicates that a <code>JavaModelException</code> should be thrown for the first
     *            unresolved variable or container
     * @return the resolved classpath for the project as a list of simple classpath entries, where all classpath
     *         variable and container entries have been resolved and substituted with their final target entries
     */
    IClasspathEntry[] getResolvedClasspath(IJavaProject javaProject, boolean ignoreUnresolvedEntry);

    /**
     * Creates and returns a new classpath entry of kind <code>CPE_CONTAINER</code> for the given path. This method is
     * fully equivalent to calling {@link #newContainerEntry(IPath, IAccessRule[], IClasspathAttribute[], boolean)
     * newContainerEntry(containerPath, new IAccessRule[0], new IClasspathAttribute[0], false)}.
     *
     * @param containerPath
     *            the path identifying the container, it must be formed of two segments
     * @return a new container classpath entry
     */
    IClasspathEntry newContainerEntry(IPath containerPath);

    /**
     * Creates and returns a new non-exported classpath entry of kind <code>CPE_LIBRARY</code> for the JAR or folder
     * identified by the given absolute path. This specifies that all package fragments within the root will have
     * children of type <code>IClassFile</code>. This method is fully equivalent to calling
     * {@link #newLibraryEntry(IPath, IPath, IPath, IAccessRule[], IClasspathAttribute[], boolean) newLibraryEntry(path,
     * sourceAttachmentPath, sourceAttachmentRootPath, new IAccessRule[0], new IClasspathAttribute[0], false)}.
     *
     * @param path
     *            the path to the library
     * @param sourceAttachmentPath
     *            the absolute path of the corresponding source archive or folder, or <code>null</code> if none. Note,
     *            since 3.0, an empty path is allowed to denote no source attachment. Since 3.4, this path can also
     *            denote a path external to the workspace. and will be automatically converted to <code>null</code>.
     * @param sourceAttachmentRootPath
     *            the location of the root of the source files within the source archive or folder or <code>null</code>
     *            if this location should be automatically detected.
     * @param isTestLib
     *            pass true if you want this library to only be visible to test classes
     * @return a new library classpath entry
     */
    IClasspathEntry newLibraryEntry(IPath path, IPath sourceAttachmentPath, IPath sourceAttachmentRootPath,
            boolean isTestLib);

    /**
     * Creates and returns a new non-exported classpath entry of kind <code>CPE_PROJECT</code> for the project
     * identified by the given absolute path.
     * <p>
     * This method is fully equivalent to calling
     * {@link #newProjectEntry(IPath, IAccessRule[], boolean, IClasspathAttribute[], boolean) newProjectEntry(path, new
     * IAccessRule[0], true, new IClasspathAttribute[0], false)}.
     * </p>
     *
     * @param path
     *            the absolute path of the binary archive
     * @return a new project classpath entry
     */
    IClasspathEntry newProjectEntry(IPath path);

    /**
     * Creates and returns a new classpath entry of kind <code>CPE_SOURCE</code> for all files in the project's source
     * folder identified by the given absolute workspace-relative path.
     * <p>
     * The convenience method is fully equivalent to:
     * </p>
     *
     * <pre>
     * newSourceEntry(path, new IPath[] {}, new IPath[] {}, null);
     * </pre>
     *
     * @param sourcePath
     *            the absolute workspace-relative path of a source folder
     * @param outputPath
     *            the absolute workspace-relative path of the output folder, null is a legal value and means the
     *            'default'
     * @param isTestSource
     *            pass true if this path contains test sources
     * @return a new source classpath entry
     * @see #newSourceEntry(IPath, IPath[], IPath[], IPath)
     */
    IClasspathEntry newSourceEntry(IPath sourcePath, IPath outputPath, boolean isTestSource);

    /**
     * Bind a container reference path to some actual containers (<code>IClasspathContainer</code>). This API must be
     * invoked whenever changes in container need to be reflected onto the JavaModel. Containers can have distinct
     * values in different projects, therefore this API considers a set of projects with their respective containers.
     * <p>
     * <code>containerPath</code> is the path under which these values can be referenced through container classpath
     * entries (<code>IClasspathEntry#CPE_CONTAINER</code>). A container path is formed by a first ID segment followed
     * with extra segments, which can be used as additional hints for the resolution. The container ID is used to
     * identify a <code>ClasspathContainerInitializer</code> registered on the extension point
     * "org.eclipse.jdt.core.classpathContainerInitializer".
     * </p>
     * <p>
     * There is no assumption that each individual container value passed in argument
     * (<code>respectiveContainers</code>) must answer the exact same path when requested
     * <code>IClasspathContainer#getPath</code>. Indeed, the containerPath is just an indication for resolving it to an
     * actual container object. It can be delegated to a <code>ClasspathContainerInitializer</code>, which can be
     * activated through the extension point "org.eclipse.jdt.core.ClasspathContainerInitializer").
     * </p>
     * <p>
     * In reaction to changing container values, the JavaModel will be updated to reflect the new state of the updated
     * container. A combined Java element delta will be notified to describe the corresponding classpath changes
     * resulting from the container update. This operation is batched, and automatically eliminates unnecessary updates
     * (new container is same as old one). This operation acquires a lock on the workspace's root.
     * </p>
     * <p>
     * This functionality cannot be used while the workspace is locked, since it may create/remove some resource
     * markers.
     * </p>
     * <p>
     * Classpath container values are persisted locally to the workspace, but are not preserved from a session to
     * another. It is thus highly recommended to register a <code>ClasspathContainerInitializer</code> for each
     * referenced container (through the extension point "org.eclipse.jdt.core.ClasspathContainerInitializer").
     * </p>
     * <p>
     * Note: setting a container to <code>null</code> will cause it to be lazily resolved again whenever its value is
     * required. In particular, this will cause a registered initializer to be invoked again.
     * </p>
     *
     * @param containerPath
     *            - the name of the container reference, which is being updated
     * @param affectedProjects
     *            - the set of projects for which this container is being bound
     * @param respectiveContainers
     *            - the set of respective containers for the affected projects
     * @param monitor
     *            a monitor to report progress
     * @throws JavaModelException
     * @see ClasspathContainerInitializer
     * @see #getClasspathContainer(IPath, IJavaProject)
     * @see IClasspathContainer
     */
    void setClasspathContainer(IPath containerPath, IJavaProject[] affectedProjects,
            IClasspathContainer[] respectiveContainers, IProgressMonitor monitor) throws JavaModelException;

}
