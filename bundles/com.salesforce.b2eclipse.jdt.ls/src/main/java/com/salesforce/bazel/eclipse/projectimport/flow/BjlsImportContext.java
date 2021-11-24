package com.salesforce.bazel.eclipse.projectimport.flow;

import java.io.File;
import java.util.List;

import com.salesforce.bazel.eclipse.project.BjlsEclipseProjectCreator;
import com.salesforce.bazel.eclipse.project.EclipseProjectCreator;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;

public class BjlsImportContext extends ImportContext {

    public BjlsImportContext(BazelPackageLocation bazelWorkspaceRootPackageInfo,
            List<BazelPackageLocation> selectedBazelPackages, ProjectOrderResolver projectOrderResolver) {
        super(bazelWorkspaceRootPackageInfo, selectedBazelPackages, projectOrderResolver);
    }

    @Override
    protected EclipseProjectCreator buildEclipseProjectCreator(File bazelWorkspaceRootDirectory,
            BazelProjectManager bazelProjectManager, ResourceHelper resourceHelper,
            BazelCommandManager bazelCommandManager) {
        return new BjlsEclipseProjectCreator(bazelWorkspaceRootDirectory, bazelProjectManager, resourceHelper,
            bazelCommandManager);
    }
}
