/*-
 *
 */
package com.salesforce.bazel.eclipse.core.setup;

import static java.lang.String.format;

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
 * Imports a Bazel workspace into an Eclipse workspace
 */
public class ImportBazelWorkspaceJob extends WorkspaceJob {

    private final IPath projectViewLocation;
    private final BazelWorkspace workspace;

    public ImportBazelWorkspaceJob(BazelWorkspace workspace, IPath projectViewLocation) {
        super(format("Import %s", workspace.getLocation()));
        this.workspace = workspace;
        this.projectViewLocation = projectViewLocation;

        if (!workspace.getLocation().isPrefixOf(projectViewLocation)) {
            throw new IllegalArgumentException(format("Project view '%s' is located outside of workspace '%s'",
                projectViewLocation, workspace.getLocation()));
        }

        // lock the full workspace (to prevent concurrent build activity)
        setRule(getWorkspaceRoot());
    }

    IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    IWorkspaceRoot getWorkspaceRoot() {
        return getWorkspace().getRoot();
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        var reader = new BazelProjectFileReader(projectViewLocation.toFile().toPath());
        BazelProjectView projectView;
        try {
            projectView = reader.read();
        } catch (IOException e) {
            throw new CoreException(Status
                    .error(format("Error reading project view '%s': %s", projectViewLocation, e.getMessage()), e));
        }

        var result = new SynchronizeProjectViewJob(workspace, projectView).runInWorkspace(monitor);
        if (result.isOK()) {
            workspace.getBazelProject().setProjectViewLocation(projectViewLocation);
        }

        return result;
    }

}
