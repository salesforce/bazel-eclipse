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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.devtools.build.lib.query2.proto.proto2api.Build;

/**
 * Internal representation of a Bazel rule. Used to capture only required data to reduce memory footprint
 */
public record Rule(String name, String ruleClass, List<String> ruleOutputList, Map<String, Attribute> attributeMap) {

    Rule(Build.Rule rule) {
        this(rule.getName(),
                rule.getRuleClass(),
                rule.getRuleOutputList(),
                rule.getAttributeList().stream().collect(HashMap::new, (map, attribute) -> {
                    // multiple attributes with the same name are not expected but can happen (https://github.com/bazelbuild/bazel/issues/20918)
                    // we therefore store the first occurrence of an attribute
                    if (BazelRuleAttribute.KNOWN_ATTRIBUTES.contains(attribute.getName())
                            && !map.containsKey(attribute.getName())) {
                        map.put(attribute.getName(), new Attribute(attribute));
                    }
                }, HashMap::putAll));
    }

    public Attribute getAttribute(String name) {
        return attributeMap.get(name);
    }

}
