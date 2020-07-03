package com.salesforce.bazel.sdk.workspace;

import java.util.List;

import com.salesforce.bazel.sdk.aspect.AspectDependencyGraphBuilder;
import com.salesforce.bazel.sdk.aspect.AspectPackageInfos;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelDependencyGraph;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Orders modules for import such that upstream dependencies are imported before downstream
 * dependencies.
 */
public class ProjectOrderResolverImpl implements ProjectOrderResolver {
	LogHelper logger;
	
    public ProjectOrderResolverImpl() {
    	logger = LogHelper.log(this.getClass());
    }

    /**
     * Orders all of the packages for import such that no package is imported before any of modules that it depends on.
     * <p>
     * Given the complex nature of the dependency graph, and the user can select an arbitrary set of packages to import,
     * this . 
     * It assumes that there are hundreds/thousands of packages in the Bazel workspace, and the
     * user will pick 10-20 to import.
     * 
     * @return ordered list of modules - leaves nodes goes first, those which dependent on them next and so on up to the
     *         root module
     */
    public Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            AspectPackageInfos aspects) {
    	List<BazelPackageLocation> selectedPackages = rootPackage.gatherChildren();
    	
    	return computePackageOrder(rootPackage, selectedPackages, aspects);
    }

    /**
     * Orders the packages selected for import such that no package is imported before any of modules that it depends on.
     * <p>
     * Given the complex nature of the dependency graph, and the user can select an arbitrary set of packages to import,
     * this . 
     * It assumes that there are hundreds/thousands of packages in the Bazel workspace, and the
     * user will pick 10-20 to import.
     * 
     * @return ordered list of modules - leaves nodes goes first, those which dependent on them next and so on up to the
     *         root module
     */
    public Iterable<BazelPackageLocation> computePackageOrder(BazelPackageLocation rootPackage,
            List<BazelPackageLocation> selectedPackages, AspectPackageInfos aspects) {

        if (aspects == null) {
            return selectedPackages;
        }
        
        // first, generate the dependency graph for the entire workspace
        List<BazelPackageLocation> orderedModules = null;
        try {
            BazelDependencyGraph workspaceDepGraph = AspectDependencyGraphBuilder.build(aspects, false);
            orderedModules = workspaceDepGraph.orderLabels(selectedPackages);

            StringBuffer sb = new StringBuffer();
            sb.append("ImportOrderResolver order of modules: ");
            for (BazelPackageLocation pkg : orderedModules) {
                sb.append(pkg.getBazelPackageName());
                sb.append("  ");
            }
            logger.debug(sb.toString());
        } catch (Exception anyE) {
            anyE.printStackTrace();
            orderedModules = selectedPackages;
        }
        return orderedModules;

    }
}