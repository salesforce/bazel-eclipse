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
package com.salesforce.bazel.sdk.model;

import java.nio.file.Path;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;

/**
 * Internal representation of a Bazel rule. Used to capture only required data to reduce memory footprint
 */
public record RuleInternal(
        String name,
        String ruleClass,
        List<String> ruleOutputList,
        ListMultimap<String, AttributeInternal> attributeMap,
        Path workspaceRoot) {

    //An illegal character for a path
    static final String WORKSPACE_PATH_PACEHOLDER = "|";

    RuleInternal(Rule rule, Path workspaceRoot) {
        this(rule.getName(),
                rule.getRuleClass(),
                rule.getRuleOutputList(),
                rule.getAttributeList()
                        .stream()
                        .collect(
                            ArrayListMultimap::create,
                            (map, attribute) -> map.put(attribute.getName(), new AttributeInternal(attribute)),
                            Multimap::putAll),
                workspaceRoot);
    }

    public List<AttributeInternal> getAttributes(String name) {
        return attributeMap.get(name);
    }

}
