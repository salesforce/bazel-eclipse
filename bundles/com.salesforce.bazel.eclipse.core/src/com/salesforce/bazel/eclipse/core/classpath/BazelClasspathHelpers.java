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
 *      Salesforce - Adapted from M2E
*/

package com.salesforce.bazel.eclipse.core.classpath;

import java.util.stream.Stream;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

public class BazelClasspathHelpers {

    public static String getAttribute(IClasspathEntry entry, String key) {
        if ((entry == null) || (entry.getExtraAttributes().length == 0) || (key == null)) {
            return null;
        }
        return Stream.of(entry.getExtraAttributes()).filter(a -> key.equals(a.getName())).findFirst()
                .map(IClasspathAttribute::getValue).orElse(null);
    }

    public static IClasspathEntry getBazelContainerEntry(IJavaProject project) {
        if (project == null) {
            return null;
        }

        try {
            for (IClasspathEntry entry : project.getRawClasspath()) {
                if (isBazelClasspathContainer(entry.getPath())) {
                    return entry;
                }
            }
        } catch (JavaModelException ex) {
            // ignore
        }
        return null;
    }

    public static boolean isBazelClasspathContainer(IPath containerPath) {
        return (containerPath != null) && (containerPath.segmentCount() > 0)
                && IClasspathContainerConstants.CONTAINER_ID.equals(containerPath.segment(0));
    }
}
