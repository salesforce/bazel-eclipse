package com.salesforce.bazel.sdk.graph;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.salesforce.bazel.sdk.model.BazelPackageLocation;

public abstract class BazelDependencyGraph {

    // CONSTRUCTION

    /**
     * Makes a dependency from source -> dep at the package level. For some use cases, package level dependencies are
     * sufficient (//a/b/c), but in other cases target level dependencies are needed (//a/b/c:d). The caller of this
     * method should provide as much detail as is available.
     *
     * @param sourceLabel
     *            the label for the source package (e.g. //a/b/c or //a/b/c:d)
     * @param depLabel
     *            the label for the depended-on package (e.g. //foo or //foo:bar)
     */
    public abstract void addDependency(String sourceLabel, String depLabel);

    // LOOKUPS

    /**
     * Provides a map for tracking forward deps. The key is the label as a string, and the value is the set of
     * dependencies (as labels) for the source.
     */
    public abstract Map<String, Set<String>> getDependsOnMap();

    /**
     * Provides a map for tracking reverse deps. The key is the label as a string, and the value is the set of sources
     * (as labels) that depend on the label.
     */
    public abstract Map<String, Set<String>> getUsedByMap();

    /**
     * Returns the set of labels that are not dependencies for other labels in the workspace. If A depends on B, which
     * depends on C, this method will return A.
     */
    public abstract Set<String> getRootLabels();

    /**
     * Returns the set of labels that exist as dependencies to other labels in the workspace, and do not have any
     * dependencies on other labels. If A depends on B, which depends on C, this method will return C. If a label stands
     * alone (i.e. it has not dependency, and is not depended on by another node, it is not included).
     */
    public abstract Set<String> getLeafLabels();

    /**
     * Returns the set of labels that exist as dependencies to other labels in the workspace, and do not have any
     * dependencies on other labels. If A depends on B, which depends on C, this method will return C. If a label stands
     * alone (i.e. it has not dependency, and is not depended on by another node, it is not included).
     * <p>
     * If a label only depends on external deps (e.g. @maven//:com_spring_etc), it will be considered a leaf node only
     * if the passed ignoreExternals is set to true
     */
    public abstract Set<String> getLeafLabels(boolean ignoreExternals);

    // ANALYSIS

    /**
     * Using the computed dependency graph, order the passed labels such that no label appears in the list prior to any
     * label it depends on.
     * <p>
     * Note that there is almost always multiple valid solutions for any given graph+label selection.
     */
    public abstract List<BazelPackageLocation> orderLabels(Set<BazelPackageLocation> selectedLabels);

    /**
     * Using the computed dependency graph, order the passed labels such that no label appears in the list prior to any
     * label it depends on.
     * <p>
     * Note that there is almost always multiple valid solutions for any given graph+label selection.
     */
    public abstract List<BazelPackageLocation> orderLabels(List<BazelPackageLocation> selectedLabels);

    /**
     * Using the computed dependency graph, order the passed labels such that no label appears in the list prior to any
     * label it depends on.
     * <p>
     * Note that there is almost always multiple valid solutions for any given graph+label selection.
     * <p>
     * Since this method is mostly used to order packages within the active Bazel workspace, you most often will not
     * want to navigate into the dependency graph of the external dependencies (e.g. maven) while building the
     * dependency graph. Pass false to followExternalTransitives to trigger this performance optimization.
     */
    public abstract List<BazelPackageLocation> orderLabels(List<BazelPackageLocation> selectedLabels,
            boolean followExternalTransitives);

    /**
     * Depth first search to determine if the passed <i>possibleDependency</i> is a direct or transitive dependency of
     * the pass <i>label</i>
     *
     * @param label
     * @param possibleDependency
     * @return
     */
    public abstract boolean isDependency(String label, String possibleDependency);

    /**
     * Depth first search to determine if the passed <i>possibleDependency</i> is a direct or transitive dependency of
     * the passed <i>label</i>. This version of the method allows the caller to pass a cache object (opaque). If you
     * will call isDependency many times, with repetitive crawls of the dependency graph, the cache will be used so we
     * only compute areas of the graph once.
     *
     * @param label
     * @param possibleDependency
     * @param depCache
     */
    public abstract boolean isDependency(String label, String possibleDependency, Map<String, Boolean> depCache);

    /**
     * Depth first search to determine if the passed <i>possibleDependency</i> is a direct or transitive dependency of
     * the passed <i>label</i>. This version of the method allows the caller to pass a cache object (opaque). If you
     * will call isDependency many times, with repetitive crawls of the dependency graph, the cache will be used so we
     * only compute areas of the graph once.
     *
     * @param label
     * @param possibleDependency
     * @param depCache
     * @param followExternalTransitives
     *            if false, will not look for the possibleDependency if it is a transitive of an external dependency
     *            (e.g. //abc depends on @maven//:foo which depends on @maven//:bar; this method will return false for
     *            label=//abc and possibleDependency=@maven//:bar). This is a performance optimization.
     */
    public abstract boolean isDependency(String label, String possibleDependency, Map<String, Boolean> depCache,
            boolean followExternalTransitives);

}
