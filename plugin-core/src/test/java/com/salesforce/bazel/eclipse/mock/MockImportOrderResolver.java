package com.salesforce.bazel.eclipse.mock;

import java.util.List;

import com.salesforce.bazel.eclipse.config.ImportOrderResolver;
import com.salesforce.bazel.sdk.model.AspectPackageInfos;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

public class MockImportOrderResolver implements ImportOrderResolver {

    @Override
    public Iterable<BazelPackageLocation> resolveModulesImportOrder(BazelPackageLocation rootModule,
            List<BazelPackageLocation> childModules, AspectPackageInfos aspects) {
        return childModules;
    }


}
