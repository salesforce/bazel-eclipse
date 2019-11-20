package com.salesforce.bazel.eclipse.mock;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import com.salesforce.bazel.eclipse.runtime.JavaCoreHelper;

public class MockJavaCoreHelper implements JavaCoreHelper {
    private static final String UOE_MSG = "MockJavaCoreHelper is pay as you go, you have hit a method that is not implemented."; 

    Map<String, MockIJavaProject> javaProjects = new TreeMap<>();
    
    // IMPLEMENTED METHODS
    
    @Override
    public IJavaProject getJavaProjectForProject(IProject project) {
        String projectName = project.getName();
        MockIJavaProject mockJavaProject = javaProjects.get(projectName);
        if (mockJavaProject == null) {
            mockJavaProject = new MockIJavaProject(project);
            javaProjects.put(projectName, mockJavaProject);
        }
        return mockJavaProject;
    }

    @Override
    public IClasspathEntry[] getRawClasspath(IJavaProject javaProject) {
        try {
            return javaProject.getRawClasspath();
        } catch (Exception anyE) {
            anyE.printStackTrace();
        }
        return null;
    }

    @Override
    public IClasspathEntry[] getResolvedClasspath(IJavaProject javaProject, boolean ignoreUnresolvedEntry) {
        try {
            return javaProject.getResolvedClasspath(ignoreUnresolvedEntry);
        } catch (Exception anyE) {
            anyE.printStackTrace();
        }
        return null;
    }

    @Override
    public IClasspathEntry newSourceEntry(IPath path) {
        return new MockIClasspathEntry(IClasspathEntry.CPE_SOURCE, path);
    }

    @Override
    public IClasspathEntry newContainerEntry(IPath containerPath) {
        return new MockIClasspathEntry(IClasspathEntry.CPE_CONTAINER, containerPath);
    }

    @Override
    public IClasspathEntry newProjectEntry(IPath path) {
        return new MockIClasspathEntry(IClasspathEntry.CPE_PROJECT, path);
    }

    @Override
    public IClasspathEntry newLibraryEntry(IPath path, IPath sourceAttachmentPath, IPath sourceAttachmentRootPath) {
        return new MockIClasspathEntry(IClasspathEntry.CPE_LIBRARY, path);
    }

    @Override
    public IJavaProject[] getAllJavaProjects() {
        return javaProjects.keySet().toArray(new IJavaProject[] {});
    }
    
    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.
    
    @Override
    public void setClasspathContainer(IPath containerPath, IJavaProject[] affectedProjects,
            IClasspathContainer[] respectiveContainers, IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IJavaModel getJavaModelForWorkspace(IWorkspaceRoot root) {
        throw new UnsupportedOperationException(UOE_MSG);
    }


}
