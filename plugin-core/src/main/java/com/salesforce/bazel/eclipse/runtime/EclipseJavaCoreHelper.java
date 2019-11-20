package com.salesforce.bazel.eclipse.runtime;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;

/**
 * Facade for the Eclipse JavaCore singleton.
 */
public class EclipseJavaCoreHelper implements JavaCoreHelper {

    @Override
    public void setClasspathContainer(IPath containerPath, IJavaProject[] affectedProjects,
            IClasspathContainer[] respectiveContainers, IProgressMonitor monitor) throws JavaModelException {
        JavaCore.setClasspathContainer(containerPath, affectedProjects, respectiveContainers, monitor);
    }

    @Override
    public IClasspathEntry[] getRawClasspath(IJavaProject javaProject) {
        try {
            return javaProject.getRawClasspath();
        } catch (JavaModelException ex) {
            // TODO this doesn't seem right
            return new IClasspathEntry[] {};
        }
    }

    @Override
    public IClasspathEntry[] getResolvedClasspath(IJavaProject javaProject, boolean ignoreUnresolvedEntry) {
        try {
            return javaProject.getResolvedClasspath(ignoreUnresolvedEntry);
        } catch (JavaModelException ex) {
            // TODO this doesn't seem right
            return new IClasspathEntry[] {};
        }
    }

    @Override
    public IJavaModel getJavaModelForWorkspace(IWorkspaceRoot root) {
        return JavaCore.create(root);
    }

    @Override
    public IJavaProject getJavaProjectForProject(IProject project) {
        return JavaCore.create(project);
    }

    @Override
    public IClasspathEntry newSourceEntry(IPath path) {
        return JavaCore.newSourceEntry(path);
    }

    @Override
    public IClasspathEntry newContainerEntry(IPath containerPath) {
        return JavaCore.newContainerEntry(containerPath);
    }

    @Override
    public IClasspathEntry newProjectEntry(IPath path) {
        return JavaCore.newProjectEntry(path);
    }

    @Override
    public IClasspathEntry newLibraryEntry(IPath path, IPath sourceAttachmentPath, IPath sourceAttachmentRootPath) {
        return JavaCore.newLibraryEntry(path, sourceAttachmentPath, sourceAttachmentRootPath);
    }

    public IJavaProject[] getAllJavaProjects() {
        IWorkspaceRoot eclipseWorkspaceRoot = BazelPluginActivator.getResourceHelper().getEclipseWorkspaceRoot();
        try {
            IJavaModel eclipseWorkspaceJavaModel = this.getJavaModelForWorkspace(eclipseWorkspaceRoot); 
            return eclipseWorkspaceJavaModel.getJavaProjects();
        } catch (JavaModelException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
