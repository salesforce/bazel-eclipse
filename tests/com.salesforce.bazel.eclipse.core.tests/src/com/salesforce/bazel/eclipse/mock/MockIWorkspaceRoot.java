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
import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.FileInfoMatcherDescription;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceFilterDescription;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

import com.salesforce.bazel.eclipse.BazelPluginActivator;

public class MockIWorkspaceRoot implements IWorkspaceRoot {
    private static final String UOE_MSG =
            "MockIWorkspaceRoot is pay as you go, you have hit a method that is not implemented.";

    private MockEclipse mockEclipse;
    private File eclipseWorkspaceDir;

    /**
     * This is where we stored the linked folder bookkeeping.
     * <p>
     * The key is the virtual path in the workspace: /Users/plaird/dev/runtime-InnerEclipse-413/apple-api/src/main/java
     * $SLASH_OK: sample code The value is the actual path in the Bazel workspace:
     * /Users/plaird/dev/simplejava/projects/libs/apple/apple-api/src/main/java $SLASH_OK: sample code
     * <p>
     * Note that the path keys are made absolute before storing in the map, and you must do the same prior to lookups:
     * IPath path = ... linkedFolders.get(path.makeAbsolute().toOSString());
     */
    public final Map<String, IFolder> linkedFolders = new TreeMap<>();

    /**
     * This is where we stored the linked file bookkeeping.
     * <p>
     * The key is the virtual path in the workspace: /Users/plaird/dev/runtime-InnerEclipse-413/apple-api/src/main/java
     * $SLASH_OK: sample code The value is the actual path in the Bazel workspace:
     * /Users/plaird/dev/simplejava/projects/libs/apple/apple-api/src/main/java $SLASH_OK: sample code
     * <p>
     * Note that the path keys are made absolute before storing in the map, and you must do the same prior to lookups:
     * IPath path = ... linkedFiles.get(path.makeAbsolute().toOSString());
     */
    public final Map<String, IFile> linkedFiles = new TreeMap<>();

    public MockIWorkspaceRoot(MockEclipse mockEclipse, File eclipseWorkspaceDir) {
        this.mockEclipse = mockEclipse;
        this.eclipseWorkspaceDir = eclipseWorkspaceDir;
    }

    // IMPLEMENTED METHODS

    @Override
    public IResource findMember(IPath path) {
        IResource res = null;
        if (path == null) {
            System.err.println(
                "MockIWorkspaceRoot.findMember was called with a null 'path' parameter, which is suspicious.");
            return null;
        }
        String[] segments = path.segments();

        if (segments.length == 0) {
            System.err.println(
                "MockIWorkspaceRoot.findMember was called with an empty 'path' parameter, which is suspicious.");
        } else if (segments.length == 1) {
            // if there is only one token, it is a project name and we will return the IProject
            // which will have a location in the Eclipse workspace directory
            String firstSegment = segments[0];
            res = mockEclipse.getImportedProject(firstSegment);
        } else {
            // otherwise we are probably looking for a linked file/folder
            // TODO this isn't true in all case, if you overlay your Eclipse and Bazel workspace directories (ugh, who does that) this will not be true
            res = linkedFolders.get(path.makeAbsolute().toOSString());
            if (res == null) {
                res = linkedFiles.get(path.makeAbsolute().toOSString());
            }
        }

        return res;
    }

    @Override
    public URI getLocationURI() {
        return eclipseWorkspaceDir.toURI();
    }

    @Override
    public IWorkspace getWorkspace() {
        return BazelPluginActivator.getResourceHelper().getEclipseWorkspace();
    }

    @Override
    public IProject[] getProjects() {
        return mockEclipse.getImportedProjectsList().toArray(new IProject[] {});
    }

    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.

    @Override
    public boolean exists(IPath path) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource findMember(String path) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource findMember(String path, boolean includePhantoms) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource findMember(IPath path, boolean includePhantoms) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getDefaultCharset() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getDefaultCharset(boolean checkImplicit) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IFile getFile(IPath path) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IFolder getFolder(IPath path) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource[] members() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource[] members(boolean includePhantoms) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource[] members(int memberFlags) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IFile[] findDeletedMembersWithHistory(int depth, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDefaultCharset(String charset) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDefaultCharset(String charset, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResourceFilterDescription createFilter(int type, FileInfoMatcherDescription matcherDescription,
            int updateFlags, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResourceFilterDescription[] getFilters() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void accept(IResourceProxyVisitor visitor, int memberFlags) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void accept(IResourceProxyVisitor visitor, int depth, int memberFlags) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void accept(IResourceVisitor visitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void accept(IResourceVisitor visitor, int depth, boolean includePhantoms) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void accept(IResourceVisitor visitor, int depth, int memberFlags) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void clearHistory(IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void copy(IPath destination, boolean force, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void copy(IPath destination, int updateFlags, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void copy(IProjectDescription description, boolean force, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void copy(IProjectDescription description, int updateFlags, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IMarker createMarker(String type) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResourceProxy createProxy() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void delete(boolean force, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void delete(int updateFlags, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void deleteMarkers(String type, boolean includeSubtypes, int depth) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean exists() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IMarker findMarker(long id) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IMarker[] findMarkers(String type, boolean includeSubtypes, int depth) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public int findMaxProblemSeverity(String type, boolean includeSubtypes, int depth) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getFileExtension() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getFullPath() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public long getLocalTimeStamp() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getLocation() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IMarker getMarker(long id) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public long getModificationStamp() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPathVariableManager getPathVariableManager() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IContainer getParent() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Map<QualifiedName, String> getPersistentProperties() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getPersistentProperty(QualifiedName key) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IProject getProject() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getProjectRelativePath() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getRawLocation() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public URI getRawLocationURI() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ResourceAttributes getResourceAttributes() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Map<QualifiedName, Object> getSessionProperties() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Object getSessionProperty(QualifiedName key) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public int getType() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isAccessible() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isDerived() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isDerived(int options) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isHidden() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isHidden(int options) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isLinked() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isVirtual() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isLinked(int options) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isLocal(int depth) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isPhantom() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isReadOnly() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isSynchronized(int depth) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isTeamPrivateMember() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isTeamPrivateMember(int options) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void move(IPath destination, boolean force, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void move(IPath destination, int updateFlags, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void move(IProjectDescription description, boolean force, boolean keepHistory, IProgressMonitor monitor)
            throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void move(IProjectDescription description, int updateFlags, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void refreshLocal(int depth, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void revertModificationStamp(long value) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDerived(boolean isDerived) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDerived(boolean isDerived, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setHidden(boolean isHidden) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setLocal(boolean flag, int depth, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public long setLocalTimeStamp(long value) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setPersistentProperty(QualifiedName key, String value) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setResourceAttributes(ResourceAttributes attributes) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setSessionProperty(QualifiedName key, Object value) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setTeamPrivateMember(boolean isTeamPrivate) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void touch(IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean contains(ISchedulingRule rule) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isConflicting(ISchedulingRule rule) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void delete(boolean deleteContent, boolean force, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IContainer[] findContainersForLocation(IPath location) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IContainer[] findContainersForLocationURI(URI location) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IContainer[] findContainersForLocationURI(URI location, int memberFlags) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IFile[] findFilesForLocation(IPath location) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IFile[] findFilesForLocationURI(URI location) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IFile[] findFilesForLocationURI(URI location, int memberFlags) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IContainer getContainerForLocation(IPath location) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IFile getFileForLocation(IPath location) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IProject getProject(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IProject[] getProjects(int memberFlags) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
