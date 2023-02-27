/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.core.BazelCorePluginSharedContstants;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;

/**
 * Facade for the Eclipse JavaCore singleton.
 */
public class EclipseJavaCoreHelper implements JavaCoreHelper {

    @Override
    public IJavaProject[] getAllBazelJavaProjects(boolean includeBazelWorkspaceRootProject) {
        // cache all of this?
        var eclipseWorkspaceRoot = ComponentContext.getInstance().getResourceHelper().getEclipseWorkspaceRoot();
        try {
            var eclipseWorkspaceJavaModel = getJavaModelForWorkspace(eclipseWorkspaceRoot);
            var javaProjects = eclipseWorkspaceJavaModel.getJavaProjects();
            List<IJavaProject> bazelProjects = new ArrayList<>(javaProjects.length);
            for (IJavaProject candidate : javaProjects) {
                var p = candidate.getProject();
                if (p.getNature(BazelCorePluginSharedContstants.BAZEL_NATURE_ID) != null) {
                    var isRootProject = ComponentContext.getInstance().getResourceHelper().isBazelRootProject(p);
                    if (includeBazelWorkspaceRootProject || !isRootProject) {
                        bazelProjects.add(candidate);
                    }
                }
            }
            return bazelProjects.toArray(new IJavaProject[bazelProjects.size()]);
        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
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
    public IClasspathEntry newContainerEntry(IPath containerPath) {
        return JavaCore.newContainerEntry(containerPath);
    }

    @Override
    public IClasspathEntry newLibraryEntry(IPath path, IPath sourceAttachmentPath, IPath sourceAttachmentRootPath,
            boolean isTestLib) {
        IClasspathAttribute[] extraAttributes = {};
        if (isTestLib) {
            var testAttr = JavaCore.newClasspathAttribute(IClasspathAttribute.TEST, "true");
            extraAttributes = new IClasspathAttribute[] { testAttr };
        }
        IAccessRule[] accessRules = null;
        var isExported = false;

        return JavaCore.newLibraryEntry(path, sourceAttachmentPath, sourceAttachmentRootPath, accessRules,
            extraAttributes, isExported);
    }

    @Override
    public IClasspathEntry newProjectEntry(IPath path) {
        return JavaCore.newProjectEntry(path);
    }

    @Override
    public IClasspathEntry newSourceEntry(IPath sourcePath, IPath outputPath, boolean isTestSource) {
        IPath[] inclusionPatterns = {};
        IPath[] exclusionPatterns = {};
        IClasspathAttribute[] extraAttributes = {};
        if (isTestSource) {
            var testAttr = JavaCore.newClasspathAttribute(IClasspathAttribute.TEST, "true");
            extraAttributes = new IClasspathAttribute[] { testAttr };
        }

        return JavaCore.newSourceEntry(sourcePath, inclusionPatterns, exclusionPatterns, outputPath, extraAttributes);
    }

    @Override
    public void setClasspathContainer(IPath containerPath, IJavaProject[] affectedProjects,
            IClasspathContainer[] respectiveContainers, IProgressMonitor monitor) throws JavaModelException {
        JavaCore.setClasspathContainer(containerPath, affectedProjects, respectiveContainers, monitor);
    }
}