package com.salesforce.bazel.sdk.workspace;

import java.util.List;

import com.salesforce.bazel.sdk.aspect.AspectPackageInfos;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Orders packages such that if A depends on B, B will appear earlier in the returned list. This can be useful for
 * applications in which processing packages should start with the leaf dependencies and move upward. For example, IDEs
 * that create a project for each package want to import in this order.
 */
public interface ProjectOrderResolver {

    /**
     * Orders all of the packages such that no package is listed before any of packages that it depends on.
     */
    Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage, AspectPackageInfos aspects);

    /**
     * Orders the packages selected such that no package is listed before any of packages that it depends on. This
     * variant only considers the list of packages passed in the passed selectedPackages list.
     */
    Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            List<BazelPackageLocation> selectedPackages, AspectPackageInfos aspects);
}