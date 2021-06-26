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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterUtils;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

/**
 * Creates Eclipse Projects (IProject instances) during Project Import.
 */
class EclipseProjectCreator {

    private static final LogHelper LOG = LogHelper.log(EclipseProjectCreator.class);

    private final File bazelWorkspaceRootDirectory;

    EclipseProjectCreator(File bazelWorkspaceRootDirectory) {
        this.bazelWorkspaceRootDirectory = Objects.requireNonNull(bazelWorkspaceRootDirectory);
    }

    IProject createRootProject(String projectName) {
        IProject rootProject = createProject(projectName, "", Collections.emptyList(), Collections.emptyList());
        if (rootProject == null) {
            throw new IllegalStateException(
                    "Could not create the root workspace project. Look back in the log for more details.");
        }
        return rootProject;
    }

    IProject createProject(String projectName, String packageFSPath, List<String> packageSourceCodeFSPaths,
            List<BazelLabel> bazelTargets) {
        URI eclipseProjectLocation = null; // let Eclipse use the default location

        BazelProjectManager bazelProjectManager = BazelPluginActivator.getBazelProjectManager();

        IProject eclipseProject = createBaseEclipseProject(projectName, eclipseProjectLocation);
        BazelProject bazelProject = bazelProjectManager.getProject(projectName);
        try {
            ProjectImporterUtils.addNatureToEclipseProject(eclipseProject, BazelNature.BAZEL_NATURE_ID);
            ProjectImporterUtils.addNatureToEclipseProject(eclipseProject, JavaCore.NATURE_ID);
            BazelProjectManager projMgr = BazelPluginActivator.getBazelProjectManager();
            projMgr.addSettingsToProject(bazelProject, bazelWorkspaceRootDirectory.getAbsolutePath(), packageFSPath,
                bazelTargets, new ArrayList<>()); // TODO pass buildFlags
        } catch (CoreException e) {
            LOG.error(e.getMessage(), e);
        }
        return eclipseProject;
    }

    /**
     * Links files in the root of package into the Eclipse project.
     */
    void linkFilesInPackageDirectory(EclipseFileLinker fileLinker, IProject project, String packageFSPath,
            File packageDirFile, String fileExtension) {
        File[] pkgFiles = packageDirFile.listFiles();

        for (File pkgFile : pkgFiles) {
            if (pkgFile.isFile()) {
                String name = pkgFile.getName();
                if (fileExtension != null) {
                    if (!name.endsWith(fileExtension)) {
                        continue;
                    }
                }
                fileLinker.link(packageFSPath, project, name);
            }
        }
    }

    private static IProject createBaseEclipseProject(String eclipseProjectName, URI location) {
        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();
        IProgressMonitor progressMonitor = null;

        // Request the project by name, which will create a new shell IProject instance if the project doesn't already exist.
        // For this case, we expect it not to exist, but there may be a problem here if there are multiple Bazel packages
        // with the same name in different parts of the Bazel workspace (we don't support that yet).
        IProject newEclipseProject = resourceHelper.getProjectByName(eclipseProjectName);
        IProject createdEclipseProject = null;

        if (!newEclipseProject.exists()) {
            URI eclipseProjectLocation = location;
            IWorkspaceRoot workspaceRoot = resourceHelper.getEclipseWorkspaceRoot();

            // create the project description, which is initialized to:
            // 1. the given project name 2. no references to other projects 3. an empty build spec 4. an empty comment
            // to which we add the location uri
            IProjectDescription eclipseProjectDescription = resourceHelper.createProjectDescription(newEclipseProject);
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
            LOG.error("Project [{}] already exists, which is unexpected. Project initialization will not occur.",
                eclipseProjectName);
            createdEclipseProject = newEclipseProject;
        }
        BazelPluginActivator.getBazelProjectManager()
                .addProject(new BazelProject(eclipseProjectName, createdEclipseProject));
        return createdEclipseProject;
    }
}
