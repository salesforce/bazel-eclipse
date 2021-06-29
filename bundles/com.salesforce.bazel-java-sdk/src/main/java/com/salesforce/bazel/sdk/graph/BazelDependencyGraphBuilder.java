package com.salesforce.bazel.sdk.graph;

import java.util.Map;

/**
 * Interface to implement to wire up a new implementation of BazelDependencyGraph.
 */
public interface BazelDependencyGraphBuilder {

    /**
     * Method called by the BazelDependencyGraphFactory to each configured builder, until one returns a non-null graph.
     * <p>
     * The options are implementation specific. Graph implementors are expected to publish a set of supported options
     * that callers can provide.
     * <p>
     * Implementations can choose to build a graph, or return null to abstain and let another later builder build it.
     * The params may give hints as to whether this method should return a real impl or not.
     */
    BazelDependencyGraph build(String caller, Map<String, String> options);

}
