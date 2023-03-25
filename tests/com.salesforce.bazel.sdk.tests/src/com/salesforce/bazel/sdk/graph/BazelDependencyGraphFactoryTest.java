package com.salesforce.bazel.sdk.graph;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;

public class BazelDependencyGraphFactoryTest {

    private static class TestDependencyGraphBuilder implements BazelDependencyGraphBuilder {
        public boolean built = false;

        @Override
        public BazelDependencyGraph build(String caller, Map<String, String> options) {
            if (options.containsKey("pass")) {
                return null;
            }
            built = true;
            return Mockito.mock(BazelDependencyGraph.class);
        }

    }

    @Test
    public void testBuilderInvocation() {
        var origBuilders = BazelDependencyGraphFactory.builders;
        var testBuilder = new TestDependencyGraphBuilder();
        BazelDependencyGraphFactory.builders.add(0, testBuilder);
        Map<String, String> options = new HashMap<>();

        var mockGraph = BazelDependencyGraphFactory.build("testBuilderInvocation", options);
        assertTrue(testBuilder.built);
        testBuilder.built = false;
        assertNotNull(mockGraph);

        options.put("pass", "true");
        var inmemoryGraph = BazelDependencyGraphFactory.build("testBuilderInvocation", options);
        assertFalse(testBuilder.built);
        assertNotNull(inmemoryGraph);
        assertTrue(inmemoryGraph instanceof InMemoryDependencyGraph);

        BazelDependencyGraphFactory.builders = origBuilders;
    }
}
