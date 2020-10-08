package com.salesforce.bazel.sdk.aspect;

import java.util.List;

import com.salesforce.bazel.sdk.model.BazelDependencyGraph;

/**
 * Builder that uses the set of aspect infos generated for a workspace to construct the dependency graph.
 */
public class AspectDependencyGraphBuilder {

    /**
     * Builds the dependency graph using the data collected by running aspects. It is typical that the list of aspects
     * covers all packages in the Workspace, but for some use cases it may be possible to use a subset of packages.
     * <p>
     * Passing includeTarget=true will increase the complexity of the graph. It will track dependencies per target in a
     * package. Generally, this is more data than applications need. If includeTarget=false then the graph will have an
     * edge in between two packages if any target in package A depends on any target in package B.
     */
    public static BazelDependencyGraph build(AspectTargetInfos aspects, boolean includeTarget) {
        BazelDependencyGraph graph = new BazelDependencyGraph();

        for (AspectTargetInfo info : aspects.getTargetInfos()) {
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
