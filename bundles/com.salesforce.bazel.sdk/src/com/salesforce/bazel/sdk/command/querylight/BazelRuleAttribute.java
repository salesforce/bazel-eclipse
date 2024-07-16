/*-
 * Copyright (c) 2024 Salesforce and others.
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
package com.salesforce.bazel.sdk.command.querylight;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule attribute names we need
 */
public enum BazelRuleAttribute {

    SRCS("srcs"),
    EXPORTS("exports"),
    TEST_ONLY("testonly"),
    JAVAC_OPTS("javacopts"),
    RESOURCES("resources"),
    RESOURCES_STRIP_PREFIX("resource_strip_prefix"),
    STRIP_PREFIX("strip_prefix"),
    PLUGINS("plugins"),
    JARS("jars"),
    SRC_JAR("srcjar"),
    TAGS("tags"),
    NAME("name"),
    VISIBILITY("visibility"),
    PATH("path"),
    DEPS("deps");

    public static final Set<String> KNOWN_ATTRIBUTES =
            Arrays.stream(BazelRuleAttribute.values()).map(attr -> attr.key).collect(Collectors.toSet());

    public final String key;

    BazelRuleAttribute(String key) {
        this.key = key;
    }
}
