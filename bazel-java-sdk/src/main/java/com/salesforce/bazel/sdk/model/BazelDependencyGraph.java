package com.salesforce.bazel.sdk.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The Bazel dependency graph of the entire workspace. This is implemented using simple Java JDK primitives rather
 * than bring in a new dependency for the purpose. It is a directed acyclic graph with multiple root/starting nodes.
 * It is too simplistic to view the workspace as a single tree of dependencies. It is actually a set of trees that 
 * probably overlap. All we know is that it is a directed graph, and will not have cycles.
 * <p>
 * This class is oriented around use cases where you need to traverse the graph in these ways: <ul>
 * <li>starting with all the root nodes
 * <li>starting with all the leaf nodes
 * <li>random access lookup of graph information with the package label
 * <p>
 * Terminology:
 * <p>
 * Source -> Dep: if A depends on B
 * <ul>
 *  <li>Source/Dep:  if A depends on B, A is the source, B is a dep
 *  <li>Root nodes: labels that are not a dep of any other label in the workspace.
 *  <li>Leaf nodes: labels that are not a root, and have no deps. They may have external dependencies (e.g. Maven sourced jars).
 * </ul> 
 */
public class BazelDependencyGraph {
    
    // lookup maps
    Set<String> rootLabels = new LinkedHashSet<>();
    Set<String> leafLabels = new LinkedHashSet<>();
    Set<String> allSourceLabels = new HashSet<>();
    Set<String> allDepLabels = new HashSet<>();
    
    // main journals
    Map<String, Set<String>> dependsOnMap = new TreeMap<>();
    Map<String, Set<String>> usedByMap = new TreeMap<>();
    
    /**
     * Callers should use the factories to construct the graph.
     */
    public BazelDependencyGraph() {
    }
    
    // CONSTRUCTION
    
    /**
     * Makes a dependency from source -> dep
     * @param sourceLabel the label for the source package (//a/b/c)
     * @param depLabel the label for the depended-on package (//a/b/c)
     */
    public void addDependency(String sourceLabel, String depLabel) {
        allDepLabels.add(depLabel);
        allSourceLabels.add(sourceLabel);
        
        // remove source as a candidate leaf, and dep as a candidate root
        rootLabels.remove(depLabel);
        leafLabels.remove(sourceLabel);

        // if the source label is not (yet) a dep anywhere, add it to the root label list
        if (!allDepLabels.contains(sourceLabel)) {
            rootLabels.add(sourceLabel);
        }
        
        // if the dest label is not (yet) a source anywhere, add it to the leaf label list
        if (!allSourceLabels.contains(depLabel)) {
            leafLabels.add(depLabel);
        }
        
        Set<String> sourceDeps = dependsOnMap.get(sourceLabel);
        if (sourceDeps == null) {
            sourceDeps = new HashSet<>();
            dependsOnMap.put(sourceLabel, sourceDeps);
        }
        sourceDeps.add(depLabel);

        Set<String> usedbySources = usedByMap.get(depLabel);
        if (usedbySources == null) {
            usedbySources = new HashSet<>();
            usedByMap.put(depLabel, usedbySources);
        }
        usedbySources.add(sourceLabel);

    }
    
    // ACCESSORS

    /**
     * Provides a map for tracking forward deps. The key is the label as a string, and the value is the set of
     * dependencies (as labels) for the source.
     */
    public Map<String, Set<String>> getDependsOnMap() {
        return this.dependsOnMap;
    }

    /**
     * Provides a map for tracking reverse deps. The key is the label as a string, and the value is the set of
     * sources (as labels) that depend on the label.
     */
    public Map<String, Set<String>> getUsedByMap() {
        return this.usedByMap;
    }

    /**
     * Returns the set of labels that are not dependencies for other labels in the workspace.
     * If A depends on B, which depends on C, this method will return A.
     */
    public Set<String> getRootLabels() {
        return this.rootLabels;
    }

    /**
     * Returns the set of labels that exist as dependencies to other labels in the workspace, and do
     * not have any dependencies on other labels.
     * If A depends on B, which depends on C, this method will return C.
     * If a label stands alone (i.e. it has not dependency, and is not depended on by another node, it is not included).
     */
    public Set<String> getLeafLabels() {
        return this.leafLabels;
    }
    
    // ANALYSIS

    /**
     * Using the computed dependency graph, order the passed labels such that no label appears in the list
     * prior to any label it depends on.
     * <p>
     * Note that there is almost always multiple valid solutions for any given graph+label selection.
     */
    public List<BazelPackageLocation> orderLabels(Set<BazelPackageLocation> selectedLabels) {
        LinkedList<BazelPackageLocation> selectedLabelsList = new LinkedList<>();
        selectedLabelsList.addAll(selectedLabels);
        return orderLabels(selectedLabelsList);
    }
    
    /**
     * Using the computed dependency graph, order the passed labels such that no label appears in the list
     * prior to any label it depends on.
     * <p>
     * Note that there is almost always multiple valid solutions for any given graph+label selection.
     */
    public List<BazelPackageLocation> orderLabels(List<BazelPackageLocation> selectedLabels) {
        LinkedList<BazelPackageLocation> orderedLabels = null;
        
        /*
         * This is a simple algorithm. It is based on the idea that this method will be used in cases
         * in which the dependency graph can be HUGE (100,000+ edges, 10,000+ nodes) and COMPLEX (lots of overlap
         * of trees). And the user is likely working on a small subset of that graph. 
         * For example, an IDE user wants to import a handful of related packages from a monorepo in which 
         * hundreds/thousands of packages live, and most of the dependency graph is not reachable by the
         * imported packages. It still works if the selectedLabels reach most/all of the graph, it just is
         * not the most efficient solution in those cases. 
         */
        
        Map<String, Boolean> depCache = new HashMap<>();
        for (int i=0; i<selectedLabels.size(); i++) {
            /*
             * Why do we do the ordering more than once? There are cases in which a single pass, or even two,
             * will not be correct. Since we cache isDep() decisions (the expensive part) multiple passes are  
             * a cheap operation, so just do it as many times as there are elements in the selected array. 
             * In all known cases the answer will be correct. But this is not guaranteed. If you find a use case 
             * where this approach is not good enough, we will need to implement a more powerful solution.
             */
            
            orderedLabels = new LinkedList<>();
            for (BazelPackageLocation currentLabel : selectedLabels) {
                int currentIndex = 0;
                boolean inserted = false;
                for (BazelPackageLocation priorInsertedLabel : orderedLabels) {
                    if (isDependency(priorInsertedLabel.getBazelPackageName(), currentLabel.getBazelPackageName(), depCache)) {
                        // the label is a dependency of an item already in the list, we need to insert
                        orderedLabels.add(currentIndex, currentLabel);
                        inserted = true;
                        break;
                    }
                    currentIndex++;
                }
                if (!inserted) {
                    // none of the previously seen labels depends on the current label, so just add it to the end 
                    orderedLabels.add(currentLabel);
                }
            }
            selectedLabels = orderedLabels;
        }
        
        // just a reminder that the cache gets cleared to free up possibly a lot of memory if the graph is huge and the
        // ordering required traversing a lot of it
        depCache = null;
        
        return orderedLabels;
    }
    
    
    /**
     * Depth first search to determine if the passed <i>possibleDependency</i> is a direct
     * or transitive dependency of the pass <i>label</i>
     * @param label
     * @param possibleDependency
     * @return
     */
    public boolean isDependency(String label, String possibleDependency) {
        boolean isDep = isDependencyRecur(label, possibleDependency, null);
        return isDep;
    }

    /**
     * Depth first search to determine if the passed <i>possibleDependency</i> is a direct
     * or transitive dependency of the pass <i>label</i>. This version of the method allows the caller
     * to pass a cache object (opaque). If you will call isDependency many times, with repetitive crawls
     * of the dependency graph, the cache will be used so we only compute areas of the graph once.
     * 
     * @param label
     * @param possibleDependency
     * @param depCache
     */
    public boolean isDependency(String label, String possibleDependency, Map<String, Boolean> depCache) {
        boolean isDep = isDependencyRecur(label, possibleDependency, depCache);
        return isDep;
    }

    private boolean isDependencyRecur(String label, String possibleDependency, Map<String, Boolean> depCache) {
        String cacheKey = null;
        if (depCache != null) {
            cacheKey = label+"~"+possibleDependency;
            Boolean cacheValue = depCache.get(cacheKey);
            if (cacheValue != null) {
                return cacheValue;
            }
        }
        
        Set<String> dependencies = this.dependsOnMap.get(label);
        if (dependencies == null) {
            // this could be an external label, like @somejar, in which case we will not have any dep information
            return dependencyResponse(false, depCache, cacheKey);
        }
        for (String dependency : dependencies) {
            if (dependency.equals(possibleDependency)) {
                return dependencyResponse(true, depCache, cacheKey);
            }
            if (isDependencyRecur(dependency, possibleDependency, depCache)) {
                return true;
            }
        }
        return dependencyResponse(false, depCache, cacheKey);
    }
    
    private boolean dependencyResponse(boolean retval, Map<String, Boolean> depCache, String cacheKey) {
        if (depCache != null) {
            depCache.put(cacheKey, retval);
        }
        return retval;
    }
}
