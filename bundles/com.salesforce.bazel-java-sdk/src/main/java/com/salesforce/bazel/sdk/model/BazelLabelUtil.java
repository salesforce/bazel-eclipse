package com.salesforce.bazel.sdk.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class BazelLabelUtil {

    /**
     * Given a Collection of labels, groups the labels by their owning Bazel package.
     *
     * @return a mapping of package -> labels belonging to that package
     */
    public static Map<BazelLabel, Collection<BazelLabel>> groupByPackage(Collection<BazelLabel> labels) {
        Map<BazelLabel, Collection<BazelLabel>> packageToLabels = new HashMap<>();
        for (BazelLabel label : labels) {
            BazelLabel pack = label.toDefaultPackageLabel();
            Collection<BazelLabel> group = packageToLabels.get(pack);
            if (group == null) {
                group = new HashSet<>();
                packageToLabels.put(pack, group);
            }
            group.add(label);
        }
        return packageToLabels;
    }

    private BazelLabelUtil() {
        throw new IllegalArgumentException("Not meant to be instantiated");
    }


}
