package com.salesforce.bazel.eclipse.projectimport.flow;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;

public class BjlsFlowProjectImporter extends FlowProjectImporter {

    public BjlsFlowProjectImporter(ImportFlow[] flows, BazelPackageLocation bazelWorkspaceRootPackageInfo,
            List<BazelPackageLocation> selectedBazelPackages, ProjectOrderResolver projectOrderResolver,
            String executablePath, AtomicBoolean importInProgress) {
        super(flows, bazelWorkspaceRootPackageInfo, selectedBazelPackages, projectOrderResolver, executablePath,
                importInProgress);
    }

    @Override
    protected ImportContext createFlowContext() {
        return new BjlsImportContext(getBazelWorkspaceRootPackageInfo(), getSelectedBazelPackages(),
                getProjectOrderResolver());
    }
}
