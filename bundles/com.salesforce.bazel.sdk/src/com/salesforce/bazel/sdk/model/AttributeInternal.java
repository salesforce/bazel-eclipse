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

import java.util.List;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute.Discriminator;

/**
 * Internal representation of a Bazel Attribute. Used to capture only required data to reduce memory footprint
 */
public record AttributeInternal(
        String attribueString,
        List<String> attributeStringList,
        boolean attributeBoolean,
        Discriminator type) {

    AttributeInternal(Attribute from) {
        this(from.getStringValue(), from.getStringListValueList(), from.getBooleanValue(), from.getType());
    }
}
