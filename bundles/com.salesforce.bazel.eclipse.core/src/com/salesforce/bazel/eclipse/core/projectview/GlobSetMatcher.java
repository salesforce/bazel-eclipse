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
package com.salesforce.bazel.eclipse.core.projectview;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A matcher for a list of globs supporting exclusions
 */
public class GlobSetMatcher {

    /**
     * We modify the glob patterns provided by the user, so that their behavior more closely matches what is expected.
     *
     * <p>
     * Rules:
     * <li>path/ => path*
     * <li>path/* => path*
     * <li>path => path*
     *
     * @see https://github.com/bazelbuild/intellij/blob/6141ed730edd9751768497f2a6c94279fde2877e/base/src/com/google/idea/blaze/base/sync/projectview/SourceTestConfig.java#L44C1-L58C4
     */
    static String modifyPattern(String pattern) {
        pattern = trimEnd(pattern, '*');
        pattern = trimEnd(pattern, File.separatorChar);
        return pattern + "*";
    }

    private static String trimEnd(String pattern, char c) {
        var index = pattern.lastIndexOf(c);
        while ((index > 0) && (index == (pattern.length() - 1))) {
            pattern = pattern.substring(0, index - 1);
            index = pattern.lastIndexOf(c);
        }
        return pattern;
    }

    private final List<PathMatcher> matchers;

    private final List<PathMatcher> excludeMatchers;

    private final Collection<String> globs;

    public GlobSetMatcher(Collection<String> globs) {
        List<PathMatcher> matchers = new ArrayList<>();
        List<PathMatcher> excludeMatchers = new ArrayList<>();
        for (String glob : globs) {
            // there is an interesting effect in Java's glob pattern PathMatcher:
            // **/*.ext does not match "abc.ext" but "/abc.ext"; thus, **.ext would be preferred in java
            if (glob.startsWith("-")) {
                excludeMatchers
                        .add(FileSystems.getDefault().getPathMatcher("glob:" + modifyPattern(glob.substring(1))));
            } else {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + modifyPattern(glob)));
            }
        }

        // needs to be immutable (because this is used in BazelProjectView
        this.globs = Collections.unmodifiableCollection(globs);
        this.matchers = Collections.unmodifiableList(matchers);
        this.excludeMatchers = Collections.unmodifiableList(excludeMatchers);
    }

    /**
     * @return the globs
     */
    public Collection<String> getGlobs() {
        return globs;
    }

    /**
     * @param path
     *            the path to match
     * @return <code>true</code> if the path is found to be a match, <code>false</code> otherwise
     */
    public boolean matches(Path path) {
        return matchers.parallelStream().anyMatch(p -> p.matches(path))
                && !excludeMatchers.parallelStream().anyMatch(p -> p.matches(path));
    }
}
