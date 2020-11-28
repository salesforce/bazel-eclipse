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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Creates an Eclipse Project for each imported Bazel Package.
 */
public class CreateProjectsFlow implements ImportFlow {

    private static final LogHelper LOG = LogHelper.log(CreateProjectsFlow.class);

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
    public void run(ImportContext ctx) throws CoreException {
        EclipseFileLinker fileLinker = ctx.getEclipseFileLinker();
        BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();
        File bazelWorkspaceRootDirectory = ctx.getBazelWorkspaceRootDirectory();
        Iterable<BazelPackageLocation> orderedModules = ctx.getOrderedModules();

        EclipseProjectCreator projectCreator = new EclipseProjectCreator(bazelWorkspaceRootDirectory);

        List<IProject> previouslyImportedProjects = Arrays.asList(resourceHelper.getProjectsForBazelWorkspace(bazelWorkspace));
        for (BazelPackageLocation packageLocation : orderedModules) {
            if (!packageLocation.isWorkspaceRoot()) {
                String projectName = computeEclipseProjectNameForBazelPackage(packageLocation, previouslyImportedProjects, ctx.getImportedProjects());
                EclipseProjectStructureInspector inspector = new EclipseProjectStructureInspector(packageLocation);
                String packageFSPath = packageLocation.getBazelPackageFSRelativePath();
                List<BazelLabel> targets = Objects.requireNonNull(ctx.getPackageLocationToTargets().get(packageLocation));
                IProject project = projectCreator.createProject(projectName, packageFSPath, inspector.getPackageSourceCodeFSPaths(), targets);
                boolean foundFile = fileLinker.link(packageFSPath, project, "BUILD");
                if (!foundFile) {
                    foundFile = fileLinker.link(packageFSPath, project, "BUILD.bazel");
                }
                if (foundFile) {
                    ctx.addImportedProject(project, packageLocation);
                } else {
                    LOG.error("Could not find BUILD file for package {}", packageLocation.getBazelPackageFSRelativePath());
                }
            }
        }
    }

    /**
     * Uses the last token in the Bazel package token (e.g. apple-api for //projects/libs/apple-api) for the name. But if another project
     * has already been imported with the same name, start appending a number to the name until it becomes unique.
     */
    private static String computeEclipseProjectNameForBazelPackage(BazelPackageLocation packageInfo, List<IProject> previouslyImportedProjects,
            List<IProject> currentlyImportedProjectsList) {
        String packageName = packageInfo.getBazelPackageNameLastSegment();
        String finalPackageName = packageName;
        int index = 2;

        boolean foundUniqueName = false;
        while (!foundUniqueName) {
            foundUniqueName = true;
            if (doesProjectNameConflict(previouslyImportedProjects, finalPackageName) || doesProjectNameConflict(currentlyImportedProjectsList, finalPackageName)) {
                finalPackageName = packageName+index;
                index++;
                foundUniqueName = false;
            }
        }
        return finalPackageName;
    }

    private static boolean doesProjectNameConflict(List<IProject> existingProjectsList, String packageName) {
        for (IProject otherProject : existingProjectsList) {
            String otherProjectName = otherProject.getName();
            if (packageName.equals(otherProjectName)) {
                return true;
            }
        }
        return false;
    }
}
