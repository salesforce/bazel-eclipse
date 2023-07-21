/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - initial implementation similar to JDT LS importers
*/

package com.salesforce.bazel.eclipse.jdtls.managers;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE_BAZEL;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE_BZLMOD;
import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.writeString;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.setup.ImportBazelWorkspaceJob;

/**
 * Importer for Bazel projects.
 * <p>
 * The importer is registered with a priority to get triggered before Gradel, Maven, Eclipse and others. This is
 * important so we can handle Bazel projects.
 * </p>
 */
@SuppressWarnings("restriction")
public final class BazelProjectImporter extends AbstractProjectImporter {

    private static final String BAZELPROJECT = ".bazelproject";

    @Override
    public boolean applies(Collection<IPath> projectConfigurations, IProgressMonitor monitor)
            throws OperationCanceledException, CoreException {
        var configurationDirs = findProjectPathByConfigurationName(
            projectConfigurations,
            List.of(FILE_NAME_WORKSPACE_BAZEL, FILE_NAME_WORKSPACE, FILE_NAME_WORKSPACE_BZLMOD),
            false /*includeNested*/);
        if ((configurationDirs == null) || configurationDirs.isEmpty()) {
            return false;
        }

        Set<Path> noneBazelProjectPaths = new HashSet<>();
        for (IProject project : ProjectUtils.getAllProjects()) {
            if (!ProjectUtils.hasNature(project, BAZEL_NATURE_ID)) {
                noneBazelProjectPaths.add(project.getLocation().toPath());
            }
        }

        this.directories = configurationDirs.stream().filter(d -> {
            var folderIsImported = noneBazelProjectPaths.stream().anyMatch(path -> (path.compareTo(d) == 0));
            return !folderIsImported;
        }).collect(Collectors.toList());

        return !this.directories.isEmpty();
    }

    @Override
    public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
        if (directories == null) {
            var bazelDetector = new BazelFileDetector(
                    rootFolder.toPath(),
                    FILE_NAME_WORKSPACE_BAZEL,
                    FILE_NAME_WORKSPACE,
                    FILE_NAME_WORKSPACE_BZLMOD).includeNested(false);

            // exclude all existing non Bazel projects
            for (IProject project : ProjectUtils.getAllProjects()) {
                if (!ProjectUtils.hasNature(project, BAZEL_NATURE_ID)) {
                    var path = project.getLocation().toOSString();
                    bazelDetector.addExclusions(path);
                }
            }

            directories = bazelDetector.scan(monitor);
        }
        return !directories.isEmpty();
    }

    private IPath findExistingOrCreateEmptyProjectView(BazelWorkspace workspace) throws CoreException {
        // use any existing .eclipse/.bazelproject file (important: this dominates any of the logic below)
        var projectViewLocation = workspace.getBazelProjectFileSystemMapper().getProjectViewLocation();
        if (isRegularFile(projectViewLocation.toPath())) {
            return projectViewLocation;
        }

        // check of a managed "tools/intellij/.managed.bazelproject" file
        // (hard coded in Bazel IJ plug-in: https://github.com/bazelbuild/intellij/blob/bb96b4142c2d257425da2dbf7bab0e8cdb400662/base/src/com/google/idea/blaze/base/project/AutoImportProjectOpenProcessor.java#L54)
        // note: we intentionally do not support the ENV variable; scripts/automation should create .eclipse/.bazelproject file instead
        projectViewLocation = workspace.getLocation().append("tools/intellij/.managed.bazelproject");
        if (isRegularFile(projectViewLocation.toPath())) {
            return projectViewLocation;
        }

        // check for a default ".bazelproject" file in the workspace root
        projectViewLocation = workspace.getLocation().append(BAZELPROJECT);
        if (isRegularFile(projectViewLocation.toPath())) {
            return projectViewLocation;
        }

        // create an empty one in the workspace root
        JavaLanguageServerPlugin.logInfo("No .bazelproject file found. Generating a default one.");
        try {
            writeString(projectViewLocation.toPath(), """
                    # The project view file (.bazelproject) is used to import targets into the IDE.
                    #
                    # See: https://ij.bazel.build/docs/project-views.html
                    #
                    # This files provides a default experience for developers working with the project.
                    # You should customize it to suite your needs.

                    directories:
                      .

                    derive_targets_from_directories: true
                    """);
        } catch (IOException e) {
            throw new CoreException(
                    Status.error(format("Unable to create default project view at '%s'", projectViewLocation), e));
        }
        return projectViewLocation;
    }

    @Override
    public void importToWorkspace(IProgressMonitor progress) throws OperationCanceledException, CoreException {
        if ((directories == null) || directories.isEmpty()) {
            return;
        }

        JavaLanguageServerPlugin.logInfo("Importing Bazel workspace(s)");
        var monitor = SubMonitor.convert(progress, "Importing Bazel workspace(s)", directories.size() * 100);

        for (Path directory : directories) {
            var workspace = BazelCore.createWorkspace(new org.eclipse.core.runtime.Path(directory.toString()));

            // find or create project view
            var projectViewLocation = findExistingOrCreateEmptyProjectView(workspace);

            // import workspace
            // note: we don't schedule the job but execute it directly
            var importBazelWorkspaceJob = new ImportBazelWorkspaceJob(workspace, projectViewLocation);
            importBazelWorkspaceJob.runInWorkspace(monitor.split(100));
        }
    }

    @Override
    public void reset() {
        directories = null;
    }
}
