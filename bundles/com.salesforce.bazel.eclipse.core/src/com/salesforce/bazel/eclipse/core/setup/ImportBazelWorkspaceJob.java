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

import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;
import com.salesforce.bazel.eclipse.core.projectview.BazelProjectFileReader;
import com.salesforce.bazel.eclipse.core.projectview.BazelProjectView;

/**
 * Imports a Bazel workspace into an Eclipse workspace.
 */
public class ImportBazelWorkspaceJob extends WorkspaceJob {

    private final IPath projectViewToImport;
    private final BazelWorkspace workspace;

    public ImportBazelWorkspaceJob(BazelWorkspace workspace) {
        this(workspace, workspace.getLocation().append(".bazelproject"));
    }

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

    private BazelProjectView createWorkspaceProjectView() throws CoreException {
        var projectViewToImportPath = projectViewToImport.toFile().toPath();
        if (!isRegularFile(projectViewToImportPath)) {
            throw new CoreException(Status.error(format(
                "Unable to import workspace '%s'. No project view found at '%s'. Please specify a project view to import!",
                workspace.getLocation(), projectViewToImportPath)));
        }

        var workspaceProjectView =
                workspace.getBazelProjectFileSystemMapper().getProjectViewLocation().toFile().toPath();
        try {
            if (!isDirectory(workspaceProjectView.getParent())) {
                createDirectories(workspaceProjectView.getParent());
            }
            writeString(workspaceProjectView,
                format("import %s%n", workspace.getLocation().toFile().toPath().relativize(projectViewToImportPath)));
        } catch (IOException e) {
            throw new CoreException(Status.error(
                format("Error creating workspace project view '%s': %s", workspaceProjectView, e.getMessage()), e));
        }

        var reader = new BazelProjectFileReader(workspaceProjectView, workspace.getLocation().toFile().toPath());
        BazelProjectView projectView;
        try {
            projectView = reader.read();
        } catch (IOException e) {
            throw new CoreException(Status
                    .error(format("Error reading project view '%s': %s", workspaceProjectView, e.getMessage()), e));
        }
        return projectView;
    }

    IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    IWorkspaceRoot getWorkspaceRoot() {
        return getWorkspace().getRoot();
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        var projectView = createWorkspaceProjectView();
        return new SynchronizeProjectViewJob(workspace, projectView).runInWorkspace(monitor);
    }

}
