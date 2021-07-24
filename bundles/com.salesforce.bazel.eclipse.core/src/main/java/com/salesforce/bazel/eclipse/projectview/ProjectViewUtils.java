package com.salesforce.bazel.eclipse.projectview;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.project.ProjectViewConstants;

public class ProjectViewUtils {

    /**
     * Writes the project view file given the set of imported Bazel packages
     */
    public static void writeProjectViewFile(File bazelWorkspaceRootDirectory, IProject rootProject,
            List<BazelPackageLocation> importedBazelPackages) {
        ProjectView projectView =
                new ProjectView(bazelWorkspaceRootDirectory, importedBazelPackages, Collections.emptyList());
        IFile f = BazelPluginActivator.getResourceHelper().getProjectFile(rootProject,
            ProjectViewConstants.PROJECT_VIEW_FILE_NAME);

        String projectViewContent = projectView.getContent();
        IProgressMonitor monitor = null;
        boolean forceWrite = true;
        try (InputStream bis = new ByteArrayInputStream(projectViewContent.getBytes())) {
            if (f.exists()) {
                boolean keepHistory = true;
                f.setContents(bis, forceWrite, keepHistory, monitor);
            } else {
                f.create(bis, forceWrite, monitor);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
