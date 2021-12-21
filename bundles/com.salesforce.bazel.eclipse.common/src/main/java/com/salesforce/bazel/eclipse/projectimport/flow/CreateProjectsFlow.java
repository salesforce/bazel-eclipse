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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.project.EclipseFileLinker;
import com.salesforce.bazel.eclipse.project.EclipseProjectCreator;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

/**
 * Creates an Eclipse Project for each imported Bazel Package.
 */
public class CreateProjectsFlow extends AbstractImportFlowStep {

    public CreateProjectsFlow(BazelCommandManager commandManager, BazelProjectManager projectManager,
            ResourceHelper resourceHelper) {
        super(commandManager, projectManager, resourceHelper);
    }

    @Override
    public String getProgressText() {
        return "Creating Eclipse projects to contain the Bazel packages.";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getBazelWorkspaceRootDirectory());
        Objects.requireNonNull(ctx.getOrderedModules());
        Objects.requireNonNull(ctx.getJavaLanguageLevel());
        Objects.requireNonNull(ctx.getEclipseProjectCreator());
        Objects.requireNonNull(ctx.getEclipseFileLinker());
        Objects.requireNonNull(ctx.getPackageLocationToTargets());
    }

    @Override
    public int getTotalWorkTicks(ImportContext ctx) {
        return ctx.getSelectedBazelPackages().size();
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressMonitor) throws CoreException {
        EclipseFileLinker fileLinker = ctx.getEclipseFileLinker();
        final BazelWorkspace bazelWorkspace = getBazelWorkspace();
        final ResourceHelper resourceHelper = getResourceHelper();
        Iterable<BazelPackageLocation> orderedModules = ctx.getOrderedModules();
        EclipseProjectCreator projectCreator = ctx.getEclipseProjectCreator();

        List<IProject> currentImportedProjects = ctx.getImportedProjects();
        List<IProject> existingImportedProjects =
                Arrays.asList(resourceHelper.getProjectsForBazelWorkspace(bazelWorkspace));
        
        for (BazelPackageLocation packageLocation : orderedModules) {
            if (!packageLocation.isWorkspaceRoot()) {
                List<BazelLabel> bazelTargets = ctx.getPackageLocationToTargets().get(packageLocation);

                // create the project
                IProject project =
                        projectCreator.createProject(ctx, packageLocation, bazelTargets, currentImportedProjects,
                            existingImportedProjects, fileLinker, bazelWorkspace);

                if (project != null) {
                    ctx.addImportedProject(project, packageLocation);
                }

                progressMonitor.worked(1);
            }
        }
    }

}
