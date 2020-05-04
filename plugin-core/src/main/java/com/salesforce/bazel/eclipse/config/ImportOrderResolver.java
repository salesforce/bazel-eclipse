package com.salesforce.bazel.eclipse.config;

import java.util.List;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.model.AspectPackageInfo;
import com.salesforce.bazel.eclipse.model.AspectPackageInfos;
import com.salesforce.bazel.eclipse.model.BazelPackageLocation;

/**
 * Orders modules for import such that upstream dependencies are imported before downstream
 * dependencies.
 */
final class ImportOrderResolver {

    private ImportOrderResolver() {

    }

    /**
     * Builds the dependency graph of modules based on the aspects info. Current solution is a subject for optimization
     * - to many iteration inside may slow down overall import performance.
     *
     * @return ordered list of modules - leaves nodes goes first, those which dependent on them next and so on up to the
     *         root module
     */
    static Iterable<BazelPackageLocation> resolveModulesImportOrder(BazelPackageLocation rootModule,
            List<BazelPackageLocation> childModules, AspectPackageInfos aspects) {

        if (aspects == null) {
            return childModules;
        }
        
        MutableGraph<BazelPackageLocation> graph = GraphBuilder.undirected().build();

        graph.addNode(rootModule);
        for (BazelPackageLocation childPackageInfo : childModules) {
            graph.addNode(childPackageInfo);
            graph.putEdge(rootModule, childPackageInfo);
        }

        for (BazelPackageLocation childPackageInfo : childModules) {
            AspectPackageInfo packageAspect = aspects.lookByPackageName(childPackageInfo.getBazelPackageName());
            
            if (packageAspect == null) {
                throw new IllegalStateException(
                        "Package dependencies couldn't be resolved: " + childPackageInfo.getBazelPackageName());
            }
            
            for (String dep : packageAspect.getDeps()) {
                for (BazelPackageLocation candidateNode : childModules) {
                    if (dep.startsWith(candidateNode.getBazelPackageName()) && childPackageInfo != candidateNode) {
                        graph.putEdge(childPackageInfo, candidateNode);
                    }
                }
            }
        }

        Iterable<BazelPackageLocation> postOrderedModules = Traverser.forGraph(graph).depthFirstPostOrder(rootModule);

        StringBuffer sb = new StringBuffer();
        sb.append("ImportOrderResolver order of modules: ");
        for (BazelPackageLocation pkg : postOrderedModules) {
            sb.append(pkg.getBazelPackageName());
            sb.append("  ");
        }
        BazelPluginActivator.info(sb.toString());
        postOrderedModules = Traverser.forGraph(graph).depthFirstPostOrder(rootModule);
        
        return postOrderedModules;

    }

}