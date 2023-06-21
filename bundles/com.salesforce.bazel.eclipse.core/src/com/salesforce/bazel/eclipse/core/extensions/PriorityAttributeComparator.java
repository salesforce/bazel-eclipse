package com.salesforce.bazel.eclipse.core.extensions;

import java.util.Comparator;

import org.eclipse.core.runtime.IConfigurationElement;

/**
 * A {@link Comparator} for Eclipse extension registry {@link IConfigurationElement elements} which uses an integer
 * attribute named {@value #ATTR_NAME_PRIORITY}.
 */
public final class PriorityAttributeComparator implements Comparator<IConfigurationElement> {

    public static final String ATTR_NAME_PRIORITY = "priority";

    public static final PriorityAttributeComparator INSTANCE = new PriorityAttributeComparator();

    @Override
    public int compare(IConfigurationElement o1, IConfigurationElement o2) {
        var p1 = safeParse(o1.getAttribute(ATTR_NAME_PRIORITY));
        var p2 = safeParse(o2.getAttribute(ATTR_NAME_PRIORITY));

        return p2 - p1; // higher priority is more important
    }

    private int safeParse(String priority) {
        try {
            return priority != null ? Integer.parseInt(priority) : 10;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}