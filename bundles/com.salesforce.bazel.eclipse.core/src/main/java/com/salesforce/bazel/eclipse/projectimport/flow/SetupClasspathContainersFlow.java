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
package com.salesforce.bazel.eclipse.projectimport.flow;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.classpath.EclipseClasspathUtil;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;

/**
 * Configures the classpath container for each project.
 */
public class SetupClasspathContainersFlow implements ImportFlow {
    @Override
    public String getProgressText() {
        return "Configuring classpath containers";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getBazelWorkspaceRootDirectory());
        Objects.requireNonNull(ctx.getImportedProjects());
    }

    @Override
    public int getTotalWorkTicks(ImportContext ctx) {
        return ctx.getImportedProjects().size();
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressSubMonitor) throws CoreException {
        Path bazelWorkspaceRootDirectory = new Path(ctx.getBazelWorkspaceRootDirectory().getAbsolutePath());
        List<IProject> importedProjects = ctx.getImportedProjects();
        for (IProject project : importedProjects) {
            BazelPackageLocation packageLocation = ctx.getPackageLocationForProject(project);
            ProjectStructure structure = ctx.getProjectStructure(packageLocation);
            String packageFSPath = packageLocation.getBazelPackageFSRelativePath();
            IJavaProject javaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(project);

            // create the classpath; there is no return value because the cp is set directly into the passed javaProject
            EclipseClasspathUtil.createClasspath(bazelWorkspaceRootDirectory, packageFSPath, structure.getPackageSourceCodeFSPaths(),
                javaProject, ctx.getJavaLanguageLevel());

            progressSubMonitor.worked(1);
        }
    }

}
