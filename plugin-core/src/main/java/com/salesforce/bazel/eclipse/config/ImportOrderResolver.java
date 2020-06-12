package com.salesforce.bazel.eclipse.config;

import java.util.List;

import com.salesforce.bazel.eclipse.model.AspectPackageInfos;
import com.salesforce.bazel.eclipse.model.BazelPackageLocation;

/**
 * Orders modules for import.
 */
public interface ImportOrderResolver {
    
    Iterable<BazelPackageLocation> resolveModulesImportOrder(BazelPackageLocation rootModule,
            List<BazelPackageLocation> childModules, AspectPackageInfos aspects);
}