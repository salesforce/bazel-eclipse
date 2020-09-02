package com.salesforce.bazel.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class BazelDependencyGraphTest {

    @Test
    public void testSingleTree() {
        BazelDependencyGraph graph = new BazelDependencyGraph();

        graph.addDependency("rootA", "midA1");
        graph.addDependency("rootA", "midA2");
        graph.addDependency("midA1", "leafA1");
        graph.addDependency("midA1", "leafA1b");
        graph.addDependency("midA2", "leafA2");

        Set<String> roots = graph.getRootLabels();
        assertEquals(1, roots.size());
        assertTrue(roots.contains("rootA"));

        // test leaf identification
        Set<String> leaves = graph.getLeafLabels();
        assertEquals(3, leaves.size());
        assertTrue(leaves.contains("leafA1"));
        assertTrue(leaves.contains("leafA1b"));
        assertTrue(leaves.contains("leafA2"));

        // test ordering alg
        Set<BazelPackageLocation> selectedLabels = new LinkedHashSet<>();
        selectedLabels.add(new InMemoryBazelPackageLocation("midA2"));
        selectedLabels.add(new InMemoryBazelPackageLocation("rootA"));
        selectedLabels.add(new InMemoryBazelPackageLocation("leafA2"));
        List<BazelPackageLocation> orderedLabels = graph.orderLabels(selectedLabels);
        assertEquals("leafA2", orderedLabels.get(0).getBazelPackageName());
        assertEquals("midA2", orderedLabels.get(1).getBazelPackageName());
        assertEquals("rootA", orderedLabels.get(2).getBazelPackageName());
    }


    @Test
    public void testMultiDistinctTrees() {
        BazelDependencyGraph graph = new BazelDependencyGraph();

        graph.addDependency("rootA", "midA1");
        graph.addDependency("rootA", "midA2");
        graph.addDependency("midA1", "leafA1");
        graph.addDependency("midA1", "leafA1b");
        graph.addDependency("midA2", "leafA2");

        graph.addDependency("rootB", "midB1");
        graph.addDependency("rootB", "midB2");
        graph.addDependency("midB1", "leafB1");
        graph.addDependency("midB1", "leafB1b");
        graph.addDependency("midB2", "leafB2");

        Set<String> roots = graph.getRootLabels();
        assertEquals(2, roots.size());
        assertTrue(roots.contains("rootA"));
        assertTrue(roots.contains("rootB"));

        // test leaf identification
        Set<String> leaves = graph.getLeafLabels();
        assertEquals(6, leaves.size());
        assertTrue(leaves.contains("leafA1"));
        assertTrue(leaves.contains("leafA1b"));
        assertTrue(leaves.contains("leafA2"));
        assertTrue(leaves.contains("leafB1"));
        assertTrue(leaves.contains("leafB1b"));
        assertTrue(leaves.contains("leafB2"));

        // test ordering alg
        Set<BazelPackageLocation> selectedLabels = new LinkedHashSet<>();
        selectedLabels.add(new InMemoryBazelPackageLocation("rootA"));
        selectedLabels.add(new InMemoryBazelPackageLocation("midA1"));
        selectedLabels.add(new InMemoryBazelPackageLocation("leafA1"));
        selectedLabels.add(new InMemoryBazelPackageLocation("rootB"));
        List<BazelPackageLocation> orderedLabels = graph.orderLabels(selectedLabels);
        assertEquals("leafA1", orderedLabels.get(0).getBazelPackageName());
        assertEquals("midA1", orderedLabels.get(1).getBazelPackageName());
        assertEquals("rootA", orderedLabels.get(2).getBazelPackageName());
        assertEquals("rootB", orderedLabels.get(3).getBazelPackageName());
    }

    @Test
    public void testMultiConjoinedTrees() {
        BazelDependencyGraph graph = new BazelDependencyGraph();

        graph.addDependency("rootA", "mid1");
        graph.addDependency("rootA", "mid2");
        graph.addDependency("mid1", "leaf1");
        graph.addDependency("mid1", "leaf1b");
        graph.addDependency("mid2", "leaf2");

        graph.addDependency("rootB", "mid1");

        Set<String> roots = graph.getRootLabels();
        assertEquals(2, roots.size());
        assertTrue(roots.contains("rootA"));
        assertTrue(roots.contains("rootB"));

        // test leaf identification
        Set<String> leaves = graph.getLeafLabels();
        assertEquals(3, leaves.size());
        assertTrue(leaves.contains("leaf1"));
        assertTrue(leaves.contains("leaf1b"));
        assertTrue(leaves.contains("leaf2"));

        // test ordering alg
        Set<BazelPackageLocation> selectedLabels = new LinkedHashSet<>();
        selectedLabels.add(new InMemoryBazelPackageLocation("rootA"));
        selectedLabels.add(new InMemoryBazelPackageLocation("mid1"));
        selectedLabels.add(new InMemoryBazelPackageLocation("leaf1"));
        List<BazelPackageLocation> orderedLabels = graph.orderLabels(selectedLabels);
        assertEquals("leaf1", orderedLabels.get(0).getBazelPackageName());
        assertEquals("mid1", orderedLabels.get(1).getBazelPackageName());
        assertEquals("rootA", orderedLabels.get(2).getBazelPackageName());
    }
}
