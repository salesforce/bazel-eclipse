package com.salesforce.bazel.sdk.graph;

import java.util.Map;

/**
 * BazelDependencyGraphBuilder that builds InMemoryDependencyGraph instances
 */
public class InMemoryDependencyGraphBuilder implements BazelDependencyGraphBuilder {

    @Override
    public BazelDependencyGraph build(String caller, Map<String, String> options) {
        return new InMemoryDependencyGraph();
    }

}
