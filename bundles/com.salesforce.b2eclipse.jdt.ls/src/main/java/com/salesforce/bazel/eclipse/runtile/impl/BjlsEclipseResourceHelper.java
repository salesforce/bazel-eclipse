package com.salesforce.bazel.eclipse.runtile.impl;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.eclipse.runtime.impl.EclipseResourceHelper;

public class BjlsEclipseResourceHelper extends EclipseResourceHelper {
    @Override
    public void createFolderLink(IFolder thisFolder, IPath bazelWorkspaceLocation, int updateFlags,
            IProgressMonitor monitor) {
        if (!doesFolderLinkExist(thisFolder)) {
            super.createFolderLink(thisFolder, bazelWorkspaceLocation, updateFlags, monitor);
        }
    }

    private boolean doesFolderLinkExist(IFolder thisFolder) {
        IResource existing = ResourcesPlugin.getWorkspace().getRoot().findMember(thisFolder.getFullPath());
        return (existing != null && existing.isLinked());
    }
}
