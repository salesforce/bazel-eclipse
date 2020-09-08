package com.salesforce.bazel.eclipse.mock;

import java.util.List;

import com.salesforce.bazel.sdk.aspect.AspectPackageInfos;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;

public class MockImportOrderResolver implements ProjectOrderResolver {

    @Override
    public Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            AspectPackageInfos aspects) {
        return rootPackage.gatherChildren();
    }

    @Override
    public Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            List<BazelPackageLocation> childPackage, AspectPackageInfos aspects) {
        return childPackage;
    }

}
