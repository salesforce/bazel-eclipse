package com.salesforce.bazel.sdk.model;

import java.util.List;

/**
 * Builder that uses the set of aspect infos generated for a workspace to construct the
 * dependency graph.
 */
public class AspectDependencyGraphBuilder {

    
    public static BazelDependencyGraph build(AspectPackageInfos aspects, boolean includeTarget) {
        BazelDependencyGraph graph = new BazelDependencyGraph();
        
        for (AspectPackageInfo info : aspects.getPackageInfos()) {
            String sourceLabel = info.getLabel();
            if (!includeTarget) {
                sourceLabel = stripTargetFromLabel(sourceLabel);
            }
            List<String> depLabels = info.getDeps();
            for (String depLabel : depLabels) {
                if (!includeTarget) {
                    depLabel = stripTargetFromLabel(depLabel);
                }
                
                if (sourceLabel.equals(depLabel)) {
                    // this is a intra-package dependency (a common case when targets are stripped)
                    continue;
                }
                
                graph.addDependency(sourceLabel, depLabel);
            }
        }
        return graph;
    }
    
    private static String stripTargetFromLabel(String label) {
        int colonIndex = label.lastIndexOf(":");
        if (colonIndex > 0) {
            label = label.substring(0, colonIndex);
        }
        return label;
    }
    
}
