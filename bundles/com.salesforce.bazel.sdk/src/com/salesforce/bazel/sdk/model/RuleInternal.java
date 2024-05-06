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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;

/**
 * Internal representation of a Bazel rule. Used to capture only required data to reduce memory footprint
 */
public record RuleInternal(
        String name,
        String ruleClass,
        List<String> ruleOutputList,
        Map<String, AttributeInternal> attributeMap) {

    RuleInternal(Rule rule) {
        this(rule.getName(),
                rule.getRuleClass(),
                rule.getRuleOutputList(),
                rule.getAttributeList().stream().collect(HashMap::new, (map, attribute) -> {
                    if (!map.containsKey(attribute.getName())) {
                        map.put(attribute.getName(), new AttributeInternal(attribute));
                    }
                }, HashMap::putAll));
    }

    public AttributeInternal getAttribute(String name) {
        return attributeMap.get(name);
    }

}
