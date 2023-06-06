/*-
 *
 */
package com.salesforce.bazel.eclipse.core.setup;

import static java.lang.String.format;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.writeString;

import java.io.IOException;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;

/**
 * Imports a Bazel workspace into an Eclipse workspace.
 */
public class ImportBazelWorkspaceJob extends WorkspaceJob {

    private static Logger LOG = LoggerFactory.getLogger(ImportBazelWorkspaceJob.class);

    private final IPath projectViewToImport;
    private final BazelWorkspace workspace;

    public ImportBazelWorkspaceJob(BazelWorkspace workspace, IPath projectViewToImport) {
        super(format("Import %s", workspace.getLocation()));
        this.workspace = workspace;
        this.projectViewToImport = projectViewToImport;

        if (!workspace.getLocation().isPrefixOf(projectViewToImport)) {
            throw new IllegalArgumentException(format("Project view '%s' is located outside of workspace '%s'",
                projectViewToImport, workspace.getLocation()));
        }

        // lock the full workspace (to prevent concurrent build activity)
        setRule(getWorkspaceRoot());
    }

    private void createWorkspaceProjectViewIfNecessary() throws CoreException {
        var projectViewToImportPath = projectViewToImport.toPath();
        if (!isRegularFile(projectViewToImportPath)) {
            throw new CoreException(Status.error(format(
                "Unable to import workspace '%s'. No project view found at '%s'. Please specify a project view to import!",
                workspace.getLocation(), projectViewToImportPath)));
        }

        var workspaceProjectView = workspace.getBazelProjectFileSystemMapper().getProjectViewLocation().toPath();
        try {
            // don't override any existing workspace project view
            if (workspaceProjectView.equals(projectViewToImportPath) && isRegularFile(workspaceProjectView)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                        "The requested project view '{}' already exists for the Bazel workspace and will be re-used.",
                        projectViewToImportPath);
                }
                return;
            }

            // a specific .bazelproject file has been requested
            // override any existing workspace/.eclipse/.bazelproject file with importing the requested one
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating workspace view '{}' importing requested '{}' project view.", workspaceProjectView,
                    projectViewToImportPath);
            }
            if (!isDirectory(workspaceProjectView.getParent())) {
                createDirectories(workspaceProjectView.getParent());
            }
            writeString(workspaceProjectView,
                format("import %s%n", workspace.getLocation().toPath().relativize(projectViewToImportPath)));
        } catch (IOException e) {
            throw new CoreException(Status.error(
                format("Error creating workspace project view '%s': %s", workspaceProjectView, e.getMessage()), e));
        }
    }

    IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    IWorkspaceRoot getWorkspaceRoot() {
        return getWorkspace().getRoot();
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        // create the default project view if none exist yet
        createWorkspaceProjectViewIfNecessary();

        // at this point the workspace lock has been acquired because the import job used the workspace root as scheduling rule
        // thus, it's safe to call runInWorkspace directly

        return new SynchronizeProjectViewJob(workspace).runInWorkspace(monitor);
    }

}
