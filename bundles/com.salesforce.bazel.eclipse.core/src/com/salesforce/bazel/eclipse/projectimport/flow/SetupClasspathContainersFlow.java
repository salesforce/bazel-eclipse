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

import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.core.classpath.EclipseSourceClasspathUtil;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

/**
 * Configures the Source directories into the classpath container for each project.
 */
public class SetupClasspathContainersFlow extends AbstractImportFlowStep {

    private final JavaCoreHelper javaCoreHelper;

    public SetupClasspathContainersFlow(BazelCommandManager commandManager, BazelProjectManager projectManager,
            ResourceHelper resourceHelper, JavaCoreHelper javaCoreHelper) {
        super(commandManager, projectManager, resourceHelper);
        this.javaCoreHelper = javaCoreHelper;
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getBazelWorkspaceRootDirectory());
        Objects.requireNonNull(ctx.getImportedProjects());
    }

    public JavaCoreHelper getJavaCoreHelper() {
        return javaCoreHelper;
    }

    @Override
    public String getProgressText() {
        return "Configuring the source code directories into the Eclipse classpath containers.";
    }

    @Override
    public int getTotalWorkTicks(ImportContext ctx) {
        return ctx.getImportedProjects().size();
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressSubMonitor) throws CoreException {
        var bazelWorkspaceRootDirectory = new Path(ctx.getBazelWorkspaceRootDirectory().getAbsolutePath());
        var importedProjects = ctx.getImportedProjects();
        for (IProject project : importedProjects) {
            var packageLocation = ctx.getPackageLocationForProject(project);

            if (packageLocation.isWorkspaceRoot() && !ctx.isExplicitImportRootProject()) {
                // we always create an Eclipse project for the root Bazel package to hold workspace level
                // things. but in this case the user didn't ask us to import the root package targets,
                // so we don't want to setup the classpath container for the root package here
                continue;
            }

            var structure = ctx.getProjectStructure(packageLocation, getBazelWorkspace(), getCommandManager());
            var packageFSPath = packageLocation.getBazelPackageFSRelativePath();
            var javaProject = getJavaCoreHelper().getJavaProjectForProject(project);

            // create the source dirs classpath (adding each source directory to the cp, and adding the JDK); there is no
            // return value because the cp is set directly into the passed javaProject; this method also links in the
            // source directory IFolders into the project
            EclipseSourceClasspathUtil.createClasspath(bazelWorkspaceRootDirectory, packageFSPath, structure,
                javaProject, ctx.getJavaLanguageLevel(), getResourceHelper(), getJavaCoreHelper());

            progressSubMonitor.worked(1);
        }
    }

}
