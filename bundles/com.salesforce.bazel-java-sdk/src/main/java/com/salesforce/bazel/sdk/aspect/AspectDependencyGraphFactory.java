package com.salesforce.bazel.sdk.aspect;

import java.util.HashMap;
import java.util.List;

import com.salesforce.bazel.sdk.graph.BazelDependencyGraph;
import com.salesforce.bazel.sdk.graph.BazelDependencyGraphFactory;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Factory that uses the set of aspect infos generated for a workspace to construct the dependency graph.
 */
public class AspectDependencyGraphFactory {

    /**
     * Builds the dependency graph using the data collected by running aspects. It is typical that the list of aspects
     * covers all packages in the Workspace, but for some use cases it may be possible to use a subset of packages.
     * <p>
     * Passing includeTarget=true will increase the complexity of the graph. It will track dependencies per target in a
     * package. Generally, this is more data than applications need. If includeTarget=false then the graph will have an
     * edge in between two packages if any target in package A depends on any target in package B.
     */
    public static BazelDependencyGraph build(AspectTargetInfos aspects, boolean includeTarget) {
        BazelDependencyGraph graph = BazelDependencyGraphFactory.build("AspectDependencyGraphFactory", new HashMap<>());

        // TODO the stripTargetFromLabel invocations here need to be removed in order for us to solve the
        // the cyclical dependency problems tracked by https://github.com/salesforce/bazel-java-sdk/issues/23
        // the InMemoryDependencyGraph will also need to be updated to support target level edges

        for (AspectTargetInfo info : aspects.getTargetInfos()) {
            String sourcePackagePath = info.getLabelPath();
            if (!includeTarget) {
                sourcePackagePath = stripTargetFromLabel(sourcePackagePath);
            }
            List<String> depLabels = info.getDeps();
            for (String depLabel : depLabels) {
                if (!includeTarget) {
                    depLabel = stripTargetFromLabel(depLabel);
                }

                if (sourcePackagePath.equals(depLabel)) {
                    // this is a intra-package dependency (a common case when targets are stripped)
                    continue;
                }

                graph.addDependency(sourcePackagePath, depLabel);
            }
        }
        return graph;
    }

    private static String stripTargetFromLabel(String labelStr) {
        BazelLabel label = new BazelLabel(labelStr);
        if (label.isExternalRepoLabel()) {
            // this is an external workspace ref, we do not change these since they are correct as-is
            // ex:  @maven//:junit_junit
            return label.getLabelPath();
        }
        return label.getPackagePath(true);
    }

}
