/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.eclipse.core.model;

import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Describes visibility information in the Bazel graph.
 * <p>
 * This class is immutable and cannot be modified.
 * </p>
 */
public class BazelVisibility {

    public static final String PRIVATE = "//visibility:private";
    public static final String PUBLIC = "//visibility:public";
    public static final String PKG = "__pkg__";
    public static final String SUBPACKAGES = "__subpackages__";

    private final Set<String> labels;

    public BazelVisibility(List<String> labels) {
        if ((labels == null) || labels.isEmpty()) {
            throw new IllegalArgumentException("At least one visibility label must be provided!");
        }
        this.labels = new HashSet<>(requireNonNull(labels));
    }

    public BazelVisibility(String... labels) {
        if ((labels == null) || (labels.length == 0)) {
            throw new IllegalArgumentException("At least one visibility label must be provided!");
        }
        this.labels = Set.of(labels);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        var other = (BazelVisibility) obj;
        return Objects.equals(labels, other.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels);
    }

    public boolean isPublic() {
        return labels.contains(PUBLIC);
    }
}
