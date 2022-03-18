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

import static java.lang.String.format;

import java.io.File;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.project.EclipseFileLinker;
import com.salesforce.bazel.eclipse.projectview.ProjectViewUtils;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.util.BazelDirectoryStructureUtil;

/**
 * Creates the root (WORKSPACE-level) project.
 */
public class CreateRootProjectFlow extends AbstractImportFlowStep {
    private static final LogHelper LOG = LogHelper.log(CreateRootProjectFlow.class);

    public CreateRootProjectFlow(BazelCommandManager commandManager, BazelProjectManager projectManager,
            ResourceHelper resourceHelper) {
        super(commandManager, projectManager, resourceHelper);
    }

    @Override
    public String getProgressText() {
        return "Creating root project for the Eclipse workspace.";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getBazelWorkspaceRootDirectory());
        Objects.requireNonNull(ctx.getSelectedBazelPackages());
        Objects.requireNonNull(ctx.getEclipseProjectCreator());
        Objects.requireNonNull(ctx.getEclipseFileLinker());
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressMonitor) {
        File bazelWorkspaceRootDirectory = ctx.getBazelWorkspaceRootDirectory();
        if (!BazelDirectoryStructureUtil.isWorkspaceRoot(bazelWorkspaceRootDirectory)) {
            throw new IllegalArgumentException();
        }

        final ResourceHelper resourceHelper = getResourceHelper();
        final BazelWorkspace bazelWorkspace = getBazelWorkspace();
        IProject rootProject = resourceHelper.getBazelWorkspaceProject(bazelWorkspace);
        if (rootProject == null) {
            String rootProjectName = BazelNature.getEclipseRootProjectName(bazelWorkspace.getName());
            rootProject = ctx.getEclipseProjectCreator().createRootProject(ctx, bazelWorkspace, rootProjectName);
        } else if (!rootProject.isOpen()) {
            try {
                rootProject.open(progressMonitor.newChild(1));
            } catch (CoreException e) {
                throw new IllegalStateException(format("Unable to open root project '%s'!", rootProject.getName()), e);
            }
        }

        // link all files in the workspace root into the Eclipse project
        EclipseFileLinker fileLinker = ctx.getEclipseFileLinker();
        ctx.getEclipseProjectCreator().linkFilesInPackageDirectory(fileLinker, rootProject, "",
            bazelWorkspaceRootDirectory, null);

        // write the project view file which lists the imported packages
        // TODO I think this is wrong, if someone imports multiple times into the same workspace, we want a list of all projects in workspace
        //   resourceHelper.getProjectsForBazelWorkspace(bazelWorkspace)
        List<BazelPackageLocation> selectedBazelPackages = ctx.getSelectedBazelPackages();
        ProjectViewUtils.writeProjectViewFile(bazelWorkspaceRootDirectory, rootProject, selectedBazelPackages);

        boolean isImportingRootPackage = false;
        for (BazelPackageLocation pkg : selectedBazelPackages) {
            if (pkg.isWorkspaceRoot()) {
                isImportingRootPackage = true;
                break;
            }
        }
        
        // we only create a dummy source folder if we aren't importing the root package //:*
        if (!isImportingRootPackage) {
            createDummySourceFolder(rootProject);
        }

        ctx.setRootProject(rootProject);
    }

    /**
     * Dummy source folder allows to avoid exception on processing classpath for the root project
     * 
     * @param project
     *            - root project
     */
    private void createDummySourceFolder(IProject project) {
        final JavaCoreHelper javaCoreHelper = ComponentContext.getInstance().getJavaCoreHelper();
        IJavaProject javaProject = javaCoreHelper.getJavaProjectForProject(project);
        IFolder srcFolder = project.getFolder("src");
        if (srcFolder.exists()) {
            return;
        }
        try {
            IPath sourceDir = srcFolder.getFullPath();
            
            boolean force = false; // "a flag controlling how to deal with resources that are not in sync with the local file system"
            boolean local = true; // "a flag controlling whether or not the folder will be local after the creation"
            srcFolder.create(force, local, null);

            if (!sourceDir.toFile().exists()) {
                return;
            }
            
            // add the created path to the classpath as a Source cp entry
            IClasspathEntry sourceClasspathEntry = javaCoreHelper.newSourceEntry(sourceDir, null, false);
            javaProject.setRawClasspath(new IClasspathEntry[] { sourceClasspathEntry }, null);
        } catch (CoreException ex) {
            LOG.error("Error in the creation of dummy source folder for the root project", ex);
            throw new IllegalStateException(ex);
        }
    }
}
