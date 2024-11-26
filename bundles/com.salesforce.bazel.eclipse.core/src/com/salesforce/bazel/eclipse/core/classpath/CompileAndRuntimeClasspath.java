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
package com.salesforce.bazel.eclipse.core.classpath;

import java.util.Collection;
import java.util.LinkedHashSet;

import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;

/**
 * Represents a classpath as two parts, compile being the classpath entries that are loaded into the project model, and
 * transitive as classpath that are part of the transitive set for runtime only.
 */
public record CompileAndRuntimeClasspath(
        Collection<ClasspathEntry> compileEntries,
        Collection<ClasspathEntry> additionalRuntimeEntries) {

    public static class Builder {
        private final LinkedHashSet<ClasspathEntry> compileEntries = new LinkedHashSet<>();
        private final LinkedHashSet<ClasspathEntry> additionalRuntimeEntries = new LinkedHashSet<>();

        public boolean addCompileEntry(ClasspathEntry entry) {
            return compileEntries.add(entry);
        }

        public boolean addRuntimeEntry(ClasspathEntry entry) {
            return additionalRuntimeEntries.add(entry);
        }

        public CompileAndRuntimeClasspath build() {
            return new CompileAndRuntimeClasspath(compileEntries, additionalRuntimeEntries);
        }
    }
}
