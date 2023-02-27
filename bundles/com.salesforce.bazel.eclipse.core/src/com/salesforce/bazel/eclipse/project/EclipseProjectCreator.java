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
package com.salesforce.bazel.eclipse.project;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCorePluginSharedContstants;
import com.salesforce.bazel.eclipse.core.resources.BazelNature;
import com.salesforce.bazel.eclipse.projectimport.flow.ImportContext;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;
import com.salesforce.bazel.sdk.util.BazelDirectoryStructureUtil;

/**
 * Creates Eclipse Projects (IProject instances) during Project Import.
 */
public class EclipseProjectCreator {

    private static Logger LOG = LoggerFactory.getLogger(EclipseProjectCreator.class);

    private final File bazelWorkspaceRootDirectory;
    private final BazelProjectManager bazelProjectManager;
    private final ResourceHelper resourceHelper;
    private final BazelCommandManager bazelCommandManager;

    public EclipseProjectCreator(File bazelWorkspaceRootDirectory, BazelProjectManager bazelProjectManager,
            ResourceHelper resourceHelper, BazelCommandManager bazelCommandManager) {
        this.bazelWorkspaceRootDirectory = Objects.requireNonNull(bazelWorkspaceRootDirectory);
        this.bazelProjectManager = Objects.requireNonNull(bazelProjectManager);
        this.resourceHelper = Objects.requireNonNull(resourceHelper);
        this.bazelCommandManager = Objects.requireNonNull(bazelCommandManager);
    }

    private IProject createBaseEclipseProject(String eclipseProjectName, URI location, ProjectStructure structure) {
        IProgressMonitor progressMonitor = null;

        // Request the project by name, which will create a new shell IProject instance if the project doesn't already exist.
        // For this case, we expect it not to exist, but there may be a problem here if there are multiple Bazel packages
        // with the same name in different parts of the Bazel workspace (we don't support that yet).
        var newEclipseProject = resourceHelper.getProjectByName(eclipseProjectName);
        IProject createdEclipseProject = null;

        if (!newEclipseProject.exists()) {
            var eclipseProjectLocation = location;
            var workspaceRoot = resourceHelper.getEclipseWorkspaceRoot();

            // create the project description, which is initialized to:
            // 1. the given project name 2. no references to other projects 3. an empty build spec 4. an empty comment
            // to which we add the location uri
            var eclipseProjectDescription = resourceHelper.createProjectDescription(newEclipseProject);
            if ((location != null) && workspaceRoot.getLocationURI().equals(location)) {
                eclipseProjectLocation = null;
            }
            eclipseProjectDescription.setLocationURI(eclipseProjectLocation);

            try {
                createdEclipseProject =
                        resourceHelper.createProject(newEclipseProject, eclipseProjectDescription, progressMonitor);
                if (!createdEclipseProject.isOpen()) {
                    resourceHelper.openProject(createdEclipseProject, progressMonitor);
                }
            } catch (CoreException e) {
                // LOG.error(e.getMessage(), e);
                createdEclipseProject = null;
            }
        } else {
            LOG.info("Project [{}] already exists, which is unexpected. Project initialization will not occur.",
                eclipseProjectName);
            createdEclipseProject = newEclipseProject;
        }

        // create the logical bazel project
        var bazelProject = new BazelProject(eclipseProjectName, createdEclipseProject, structure);
        bazelProjectManager.addProject(bazelProject);

        return createdEclipseProject;
    }

    public IProject createProject(ImportContext ctx, BazelPackageLocation packageLocation,
            List<BazelLabel> bazelTargets, List<IProject> currentImportedProjects,
            List<IProject> existingImportedProjects, EclipseFileLinker fileLinker, BazelWorkspace bazelWorkspace) {

        var projectName =
                createProjectName(bazelWorkspace, packageLocation, currentImportedProjects, existingImportedProjects);
        var structure = ctx.getProjectStructure(packageLocation, bazelWorkspace, bazelCommandManager);
        var packageFSPath = packageLocation.getBazelPackageFSRelativePath();
        if (bazelTargets == null) {
            LOG.warn(
                "There were no supported Bazel targets found for package [{}], the package will not be imported as a project.",
                packageLocation.getBazelPackageFSRelativePath());
            return null;
        }
        List<BazelLabel> targets = Objects.requireNonNull(bazelTargets);
        IProject project;

        if (!BazelDirectoryStructureUtil.isBazelPackage(bazelWorkspaceRootDirectory, packageFSPath)) {
            LOG.error("Could not find BUILD file for package {}", packageLocation.getBazelPackageFSRelativePath());
            return null;
        }
        // create the project
        IProject eclipseProject = null;
        if (packageLocation.isWorkspaceRoot()) {
            // we will get here if there is a BUILD file in the workspace root that has interesting targets, and the
            // user chose to import this package; check if the root project has been created already
            eclipseProject = ctx.getRootProject();
        } else {
            URI eclipseProjectLocation = null; // let Eclipse use the default location
            eclipseProject = createBaseEclipseProject(projectName, eclipseProjectLocation, structure);
        }
        project = provisionProjectNature(eclipseProject, projectName, packageFSPath, structure, targets);

        // link all files in the package root into the Eclipse project
        linkFilesInPackageDirectory(fileLinker, project, packageFSPath,
            new File(bazelWorkspaceRootDirectory, packageFSPath), null);
        return project;
    }

    protected String createProjectName(BazelWorkspace bazelWorkspace, BazelPackageLocation packageLocation,
            List<IProject> currentImportedProjects, List<IProject> existingImportedProjects) {
        if (packageLocation.isWorkspaceRoot()) {
            return BazelNature.getEclipseRootProjectName(bazelWorkspace.getName());
        }

        return EclipseProjectUtils.computeEclipseProjectNameForBazelPackage(packageLocation, existingImportedProjects,
            currentImportedProjects);
    }

    public IProject createRootProject(ImportContext ctx, BazelWorkspace bazelWorkspace, String projectName) {
        // create the root Eclipse project
        var rootLocation = ctx.getBazelWorkspaceRootPackageInfo();
        var structure = ctx.getProjectStructure(rootLocation, bazelWorkspace, bazelCommandManager);
        URI eclipseProjectLocation = null; // let Eclipse use the default location
        var eclipseProject = createBaseEclipseProject(projectName, eclipseProjectLocation, structure);

        if (eclipseProject == null) {
            throw new IllegalStateException(
                    "Could not create the root workspace project. Look back in the log for more details.");
        }

        provisionProjectNature(eclipseProject, projectName, "", structure, new ArrayList<>());

        return eclipseProject;
    }

    public BazelCommandManager getBazelCommandManager() {
        return bazelCommandManager;
    }

    public BazelProjectManager getBazelProjectManager() {
        return bazelProjectManager;
    }

    public File getBazelWorkspaceRootDirectory() {
        return bazelWorkspaceRootDirectory;
    }

    public ResourceHelper getResourceHelper() {
        return resourceHelper;
    }

    /**
     * Links files in the root of package into the Eclipse project.
     */
    public void linkFilesInPackageDirectory(EclipseFileLinker fileLinker, IProject project, String packageFSPath,
            File packageDirFile, String fileExtension) {
        var pkgFiles = packageDirFile.listFiles();

        for (File pkgFile : pkgFiles) {
            if (pkgFile.isFile()) {
                var name = pkgFile.getName();
                if ((fileExtension != null) && !name.endsWith(fileExtension)) {
                    continue;
                }
                fileLinker.link(packageFSPath, project, name);
            }
        }
    }

    public IProject provisionProjectNature(IProject eclipseProject, String projectName, String packageFSPath,
            ProjectStructure structure, List<BazelLabel> bazelTargets) {

        var bazelProject = bazelProjectManager.getProject(projectName);
        try {
            EclipseProjectUtils.addNatureToEclipseProject(eclipseProject,
                BazelCorePluginSharedContstants.BAZEL_NATURE_ID, resourceHelper);
            EclipseProjectUtils.addNatureToEclipseProject(eclipseProject, JavaCore.NATURE_ID, resourceHelper);
            bazelProjectManager.addSettingsToProject(bazelProject, bazelWorkspaceRootDirectory.getAbsolutePath(),
                packageFSPath, bazelTargets, new ArrayList<>()); // TODO pass buildFlags
        } catch (CoreException e) {
            LOG.error(e.getMessage(), e);
        }
        return eclipseProject;
    }
}
