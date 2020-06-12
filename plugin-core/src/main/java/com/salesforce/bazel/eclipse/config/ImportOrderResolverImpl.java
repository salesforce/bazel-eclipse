package com.salesforce.bazel.eclipse.config;

import java.util.List;

import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.model.AspectDependencyGraphBuilder;
import com.salesforce.bazel.eclipse.model.AspectPackageInfos;
import com.salesforce.bazel.eclipse.model.BazelDependencyGraph;
import com.salesforce.bazel.eclipse.model.BazelPackageLocation;

/**
 * Orders modules for import such that upstream dependencies are imported before downstream
 * dependencies.
 */
public class ImportOrderResolverImpl implements ImportOrderResolver {

    public ImportOrderResolverImpl() {

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
        } catch (Exception anyE) {
            anyE.printStackTrace();
            orderedModules = selectedModules;
        }

        StringBuffer sb = new StringBuffer();
        sb.append("ImportOrderResolver order of modules: ");
        for (BazelPackageLocation pkg : orderedModules) {
            sb.append(pkg.getBazelPackageName());
            sb.append("  ");
        }
        BazelPluginActivator.info(sb.toString());
        
        return orderedModules;

    }
    
    // @VisibleForTesting
    static Iterable<BazelPackageLocation> orderNodes(MutableGraph<BazelPackageLocation> graph, BazelPackageLocation rootModule) {
        return Traverser.forGraph(graph).depthFirstPostOrder(rootModule);
    }

}