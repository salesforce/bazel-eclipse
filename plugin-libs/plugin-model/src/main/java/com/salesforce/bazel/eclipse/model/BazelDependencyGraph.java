package com.salesforce.bazel.eclipse.model;

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
 * Terminiology:
 * <p>
 * Source -> Dep: if A depends on B
 * <ul>
 * <li>A is source
 * <li>B is dep
 * </ul> 
 * <p>
 * Root labels: labels that are not a dep of any other label in the workspace.
 * <p>
 * Leaf Labels: labels that are not root labels, and have no dep labels. They may have external dest dependencies (e.g. Maven sourced jars).
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
     * Callers must use the factories to construct the graph.
     */
    BazelDependencyGraph() {
    }
    
    // CONSTRUCTION
    
    /**
     * Makes a dependency from source -> dep
     * @param source the label for the source package (//a/b/c)
     * @param dep the label for the depended-on package (//a/b/c)
     */
    public void addDependency(String source, String dep) {
        allDepLabels.add(dep);
        allSourceLabels.add(source);
        
        // remove source as a candidate leaf, and dep as a candidate root
        rootLabels.remove(dep);
        leafLabels.remove(source);

        // if the source label is not (yet) a dep anywhere, add it to the root label list
        if (!allDepLabels.contains(source)) {
            rootLabels.add(source);
        }
        
        // if the dest label is not (yet) a source anywhere, add it to the leaf label list
        if (!allSourceLabels.contains(dep)) {
            leafLabels.add(dep);
        }
        
        Set<String> sourceDeps = dependsOnMap.get(source);
        if (sourceDeps == null) {
            sourceDeps = new HashSet<>();
            dependsOnMap.put(source, sourceDeps);
        }
        sourceDeps.add(dep);

        Set<String> usedbySources = usedByMap.get(dep);
        if (usedbySources == null) {
            usedbySources = new HashSet<>();
            usedByMap.put(dep, usedbySources);
        }
        usedbySources.add(source);
    }
    
    // ACCESSORS

    /**
     * Provides a map for tracking forward deps. The key is the label as a string, and the value is the set of
     * dependencies for the label.
     */
    public Map<String, Set<String>> getDependsOnMap() {
        return this.dependsOnMap;
    }

    /**
     * Provides a map for tracking reverse deps. The key is the label as a string, and the value is the set of
     * sources that depend on the label.
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
     */
    public Set<String> getLeafLabels() {
        return this.leafLabels;
    }
    
    // ANALYSIS

    /**
     * Using the computed dependency graph, order the passed labels such that no label appears in the list
     * prior to any label it depends on.
     */
    public List<BazelPackageLocation> orderLabels(List<BazelPackageLocation> selectedLabels) {
        return orderLabels(new HashSet<>(selectedLabels));
    }
    
    /**
     * Using the computed dependency graph, order the passed labels such that no label appears in the list
     * prior to any label it depends on.
     */
    public List<BazelPackageLocation> orderLabels(Set<BazelPackageLocation> selectedLabels) {
        LinkedList<BazelPackageLocation> orderedLabels = new LinkedList<>();
        
        for (BazelPackageLocation currentLabel : selectedLabels) {
            int currentIndex = 0;
            boolean inserted = false;
            for (BazelPackageLocation priorInsertedLabel : orderedLabels) {
                if (isDependency(priorInsertedLabel.getBazelPackageName(), currentLabel.getBazelPackageName())) {
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
        boolean isDep = isDependencyRecur(label, possibleDependency);
        System.out.println("BazelDependencyGraph.isDependency source: "+label+" possibleDep: "+possibleDependency+ " result: "+isDep);
        return isDep;
    }
    
    private boolean isDependencyRecur(String label, String possibleDependency) {
        Set<String> dependencies = this.dependsOnMap.get(label);
        if (dependencies == null) {
            // this could be an external label, like @somejar, in which case we will not have any dep information
            return false;
        }
        for (String dependency : dependencies) {
            if (dependency.equals(possibleDependency)) {
                return true;
            }
            if (isDependencyRecur(dependency, possibleDependency)) {
                return true;
            }
        }
        return false;
    }
}
