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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.IPath;

import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;

/**
 * Represents a targets classpath as two parts, compile being the classpath entries that are loaded into the project
 * model, and transtative as classpaths that are part of the targets runtime, but are not to be loaded in the project
 * model.
 */
public record ClasspathHolder(Collection<ClasspathEntry> compile, Collection<ClasspathEntry> transitive) {

    public static class ClasspathHolderBuilder {
        // Preserve classpath order. Add leaf level dependencies first and work the way up. This
        // prevents conflicts when a JAR repackages it's dependencies. In such a case we prefer to
        // resolve symbols from the original JAR rather than the repackaged version.
        // Using accessOrdered LinkedHashMap because jars that are present in `workspaceBuilder.jdeps`
        // and in `workspaceBuilder.directDeps`, we want to treat it as a directDep
        private final Map<IPath, ClasspathEntry> compileEntries =
                new LinkedHashMap<>(/* initialCapacity= */ 32, /* loadFactor= */ 0.75f, /* accessOrder= */ true);
        private final Map<IPath, ClasspathEntry> transitiveEntries =
                new LinkedHashMap<>(/* initialCapacity= */ 32, /* loadFactor= */ 0.75f, /* accessOrder= */ true);
        private boolean hasUnloadedEntries = false;

        private final boolean partialClasspathEnabled;

        public ClasspathHolderBuilder(boolean partialClasspathEnabled) {
            this.partialClasspathEnabled = partialClasspathEnabled;
        }

        public ClasspathHolder build() {
            return new ClasspathHolder(
                    compileEntries.values(),
                    hasUnloadedEntries ? transitiveEntries.values() : Collections.emptyList());
        }

        public void put(IPath path, ClasspathEntry entry) {
            compileEntries.put(path, entry);
        }

        public void putIfAbsent(IPath path, ClasspathEntry entry) {
            compileEntries.putIfAbsent(path, entry);
        }

        public void putUnloadedIfAbsent(IPath path, ClasspathEntry entry) {
            if (!compileEntries.containsKey(path)) {
                transitiveEntries.putIfAbsent(path, entry);
                hasUnloadedEntries = true;
            }
        }
    }
}
