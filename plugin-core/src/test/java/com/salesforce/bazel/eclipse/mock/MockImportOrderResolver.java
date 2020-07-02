package com.salesforce.bazel.eclipse.mock;

import java.util.List;

import com.salesforce.bazel.sdk.aspect.AspectPackageInfos;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.ImportOrderResolver;

public class MockImportOrderResolver implements ImportOrderResolver {

    @Override
    public Iterable<BazelPackageLocation> resolveModulesImportOrder(BazelPackageLocation rootModule,
            List<BazelPackageLocation> childModules, AspectPackageInfos aspects) {
        return childModules;
    }


}
