package com.salesforce.bazel.eclipse.project;

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.IProject;

import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

public class BjlsEclipseProjectCreator extends EclipseProjectCreator {

    public BjlsEclipseProjectCreator(File bazelWorkspaceRootDirectory, BazelProjectManager bazelProjectManager,
            ResourceHelper resourceHelper, BazelCommandManager bazelCommandManager) {
        super(bazelWorkspaceRootDirectory, bazelProjectManager, resourceHelper, bazelCommandManager);
    }

    @Override
    protected String createProjectName(BazelWorkspace bazelWorkspace, BazelPackageLocation packageLocation,
            List<IProject> currentImportedProjects, List<IProject> existingImportedProjects) {
        return packageLocation.getBazelPackageNameLastSegment();
    }
}
