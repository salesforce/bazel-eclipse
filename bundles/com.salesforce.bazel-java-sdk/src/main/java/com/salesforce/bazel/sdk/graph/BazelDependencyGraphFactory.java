package com.salesforce.bazel.sdk.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BazelDependencyGraphFactory {

    /**
     * List of configured builders. Builders earlier in the list will take precedence over later builders.
     * <p>
     * If you create a new implementation of BazelDependencyGraph, add a builder for it at the head of this list so that
     * your implementation will be used instead.
     */
    public static List<BazelDependencyGraphBuilder> builders = new ArrayList<>();
    static {
        // unless the user configures a custom graph impl, the default inmemory graph will be built 
        builders.add(new InMemoryDependencyGraphBuilder());
    }

    /**
     * Finds a builder that can build a dependency graph, and builds it. After the initial build, the caller will need
     * to fill in the graph using the addDependency() method for every edge in the graph.
     * <p>
     * The caller parameter is intended to include the caller name (e.g. AspectDependencyGraphFactory) which may be used
     * to determine what type of graph is built, and/or for logging purposes. The options map is implementation specific
     * - the graphs that are configured will publish options that may be available.
     */
    public static BazelDependencyGraph build(String caller, Map<String, String> options) {
        for (BazelDependencyGraphBuilder builder : builders) {
            BazelDependencyGraph graph = builder.build(caller, options);
            if (graph != null) {
                return graph;
            }
        }
        return null;
    }

}
