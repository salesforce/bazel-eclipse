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

import com.google.devtools.build.lib.query2.proto.proto2api.Build;

/**
 * Internal representation of a Bazel target. Used to capture only required data to reduce memory footprint
 */
public record Target(Rule rule, GeneratedFile generatedFile) {
    public Target(Build.Target from) {
        this(new Rule(from.getRule()), from.hasGeneratedFile() ? new GeneratedFile(from.getGeneratedFile()) : null);
    }

    public boolean hasRule() {
        return rule != null;
    }
}
