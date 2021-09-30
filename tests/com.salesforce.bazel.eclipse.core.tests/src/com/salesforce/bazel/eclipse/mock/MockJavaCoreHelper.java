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

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;

public class MockJavaCoreHelper implements JavaCoreHelper {
    private static final String UOE_MSG =
            "MockJavaCoreHelper is pay as you go, you have hit a method that is not implemented.";

    Map<String, MockIJavaProject> javaProjects = new TreeMap<>();

    // IMPLEMENTED METHODS

    @Override
    public IJavaProject[] getAllBazelJavaProjects(boolean includeBazelWorkspaceRootProject) {
        return javaProjects.values().toArray(new IJavaProject[] {});
    }

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
        } catch (RuntimeException anyE) {
            anyE.printStackTrace();
            throw anyE;
        } catch (Exception anyE) {
            anyE.printStackTrace();
            throw new RuntimeException(anyE);
        }
    }

    @Override
    public IClasspathEntry newSourceEntry(IPath sourcePath, IPath outputPath, boolean isTestSource) {
        MockIClasspathEntry sourceEntry = new MockIClasspathEntry(IClasspathEntry.CPE_SOURCE, sourcePath);
        sourceEntry.setOutputLocation(outputPath);
        if (isTestSource) {
            MockIClasspathAttribute testAttr = new MockIClasspathAttribute(IClasspathAttribute.TEST, "true");
            sourceEntry.addExtraAttribute(testAttr);
        }
        return sourceEntry;
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
    public IClasspathEntry newLibraryEntry(IPath path, IPath sourceAttachmentPath, IPath sourceAttachmentRootPath,
            boolean isTestLib) {
        return new MockIClasspathEntry(IClasspathEntry.CPE_LIBRARY, path, sourceAttachmentPath);
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
