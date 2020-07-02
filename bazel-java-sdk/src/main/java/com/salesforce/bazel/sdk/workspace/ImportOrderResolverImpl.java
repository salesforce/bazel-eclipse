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
public class ImportOrderResolverImpl implements ImportOrderResolver {
	LogHelper logger;
	
    public ImportOrderResolverImpl() {
    	logger = LogHelper.log(this.getClass());
    }

    /**
     * Orders the modules selected for import such that no module is imported before any of modules that it depends on.
     * <p>
     * Given the complex nature of the dependency graph, and the user can select an arbitrary set of packages to import,
     * this . 
     * It assumes that there are hundreds/thousands of packages in the Bazel workspace, and the
     * user will pick 10-20 to import.
     * 
     * @return ordered list of modules - leaves nodes goes first, those which dependent on them next and so on up to the
     *         root module
     */
    public Iterable<BazelPackageLocation> resolveModulesImportOrder(BazelPackageLocation rootModule,
            List<BazelPackageLocation> selectedModules, AspectPackageInfos aspects) {

        if (aspects == null) {
            return selectedModules;
        }
        
        // first, generate the dependency graph for the entire workspace
        List<BazelPackageLocation> orderedModules = null;
        try {
            BazelDependencyGraph workspaceDepGraph = AspectDependencyGraphBuilder.build(aspects, false);
            orderedModules = workspaceDepGraph.orderLabels(selectedModules);

            StringBuffer sb = new StringBuffer();
            sb.append("ImportOrderResolver order of modules: ");
            for (BazelPackageLocation pkg : orderedModules) {
                sb.append(pkg.getBazelPackageName());
                sb.append("  ");
            }
            logger.info(sb.toString());
        } catch (Exception anyE) {
            anyE.printStackTrace();
            orderedModules = selectedModules;
        }
        return orderedModules;

    }
}