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

    @Test
    public void testBuilderInvocation() {
        List<BazelDependencyGraphBuilder> origBuilders = BazelDependencyGraphFactory.builders;
        TestDependencyGraphBuilder testBuilder = new TestDependencyGraphBuilder();
        BazelDependencyGraphFactory.builders.add(0, testBuilder);
        Map<String, String> options = new HashMap<>();

        BazelDependencyGraph mockGraph = BazelDependencyGraphFactory.build("testBuilderInvocation", options);
        assertTrue(testBuilder.built);
        testBuilder.built = false;
        assertNotNull(mockGraph);

        options.put("pass", "true");
        BazelDependencyGraph inmemoryGraph = BazelDependencyGraphFactory.build("testBuilderInvocation", options);
        assertFalse(testBuilder.built);
        assertNotNull(inmemoryGraph);
        assertTrue(inmemoryGraph instanceof InMemoryDependencyGraph);

        BazelDependencyGraphFactory.builders = origBuilders;
    }

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
}
