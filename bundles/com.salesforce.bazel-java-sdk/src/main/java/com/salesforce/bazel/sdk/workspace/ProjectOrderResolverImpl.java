package com.salesforce.bazel.sdk.workspace;

import java.util.List;

import com.salesforce.bazel.sdk.aspect.AspectDependencyGraphFactory;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfos;
import com.salesforce.bazel.sdk.graph.BazelDependencyGraph;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Orders modules for import such that upstream dependencies are imported before downstream dependencies.
 */
public class ProjectOrderResolverImpl implements ProjectOrderResolver {
    private static final LogHelper LOG = LogHelper.log(ProjectOrderResolverImpl.class);

    public ProjectOrderResolverImpl() {}

    /**
     * Orders all of the packages for import such that no package is imported before any of modules that it depends on.
     * <p>
     * Given the complex nature of the dependency graph, and the user can select an arbitrary set of packages to import,
     * this . It assumes that there are hundreds/thousands of packages in the Bazel workspace, and the user will pick
     * 10-20 to import.
     *
     * @return ordered list of modules - leaves nodes goes first, those which dependent on them next and so on up to the
     *         root module
     */
    @Override
    public Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            AspectTargetInfos aspects) {
        List<BazelPackageLocation> selectedPackages = rootPackage.gatherChildren();

        return computePackageOrder(rootPackage, selectedPackages, aspects);
    }

    /**
     * Orders the packages selected for import such that no package is imported before any of modules that it depends
     * on.
     * <p>
     * Given the complex nature of the dependency graph, and the user can select an arbitrary set of packages to import,
     * this . It assumes that there are hundreds/thousands of packages in the Bazel workspace, and the user will pick
     * 10-20 to import.
     *
     * @return ordered list of modules - leaves nodes goes first, those which dependent on them next and so on up to the
     *         root module
     */
    @Override
    public Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            List<BazelPackageLocation> selectedPackages, AspectTargetInfos aspects) {

        if (aspects == null) {
            return selectedPackages;
        }

        // first, generate the dependency graph for the entire workspace
        List<BazelPackageLocation> orderedModules = null;
        try {
            BazelDependencyGraph workspaceDepGraph = AspectDependencyGraphFactory.build(aspects, false);
            boolean followExternalTransitives = false;
            orderedModules = workspaceDepGraph.orderLabels(selectedPackages, followExternalTransitives);

            StringBuffer sb = new StringBuffer();
            sb.append("ImportOrderResolver order of modules: ");
            for (BazelPackageLocation pkg : orderedModules) {
                sb.append(pkg.getBazelPackageName());
                sb.append("  ");
            }
            LOG.debug(sb.toString());
        } catch (Exception anyE) {
            LOG.error("error computing package order", anyE);
            orderedModules = selectedPackages;
        }
        return orderedModules;

    }
}