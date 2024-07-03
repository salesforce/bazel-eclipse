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
import java.util.Optional;

import org.eclipse.core.runtime.IPath;

import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;

/**
 * Represents a targets classpath as two parts, loaded being the classpath entries that are loaded into the project
 * model, and unloaded being classpaths that are part of the target, but are not to be loaded in the project model.
 *
 * Note: Unloaded contains null values to keep the order between loaded and unloaded
 */
public record ClasspathHolder(Collection<ClasspathEntry> loaded, Collection<Optional<ClasspathEntry>> unloaded) {

    public static class ClasspathHolderBuilder {
        // Preserve classpath order. Add leaf level dependencies first and work the way up. This
        // prevents conflicts when a JAR repackages it's dependencies. In such a case we prefer to
        // resolve symbols from the original JAR rather than the repackaged version.
        // Using accessOrdered LinkedHashMap because jars that are present in `workspaceBuilder.jdeps`
        // and in `workspaceBuilder.directDeps`, we want to treat it as a directDep
        private final Map<IPath, ClasspathEntry> loadedEntries =
                new LinkedHashMap<>(/* initialCapacity= */ 32, /* loadFactor= */ 0.75f, /* accessOrder= */ true);
        private final Map<IPath, Optional<ClasspathEntry>> unloadedEntries =
                new LinkedHashMap<>(/* initialCapacity= */ 32, /* loadFactor= */ 0.75f, /* accessOrder= */ true);
        private boolean hasUnloadedEntries = false;

        private final boolean partialClasspathEnabled;

        public ClasspathHolderBuilder(boolean partialClasspathEnabled) {
            this.partialClasspathEnabled = partialClasspathEnabled;
        }

        public ClasspathHolder build() {
            return new ClasspathHolder(
                    loadedEntries.values(),
                    hasUnloadedEntries ? unloadedEntries.values() : Collections.emptyList());
        }

        public void put(IPath path, ClasspathEntry entry) {
            loadedEntries.put(path, entry);
            if (partialClasspathEnabled) {
                unloadedEntries.put(path, Optional.empty());
            }
        }

        public void putIfAbsent(IPath path, ClasspathEntry entry) {
            loadedEntries.putIfAbsent(path, entry);
            if (partialClasspathEnabled) {
                unloadedEntries.put(path, Optional.empty());
            }
        }

        public void putUnloadedIfAbsent(IPath path, ClasspathEntry entry) {
            if (!loadedEntries.containsKey(path)) {
                unloadedEntries.putIfAbsent(path, Optional.of(entry));
                hasUnloadedEntries = true;
            }
        }
    }
}
