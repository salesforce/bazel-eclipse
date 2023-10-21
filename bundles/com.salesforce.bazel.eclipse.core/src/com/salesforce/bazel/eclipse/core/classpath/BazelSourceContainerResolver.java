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
package com.salesforce.bazel.eclipse.core.classpath;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.containers.ExternalArchiveSourceContainer;
import org.eclipse.jdt.launching.sourcelookup.advanced.ISourceContainerResolver;

import com.salesforce.bazel.eclipse.core.util.jar.SourceJarFinder;

/**
 * A resolver to find source code for Bazel classpath container entries
 */
public class BazelSourceContainerResolver implements ISourceContainerResolver {

    @Override
    public Collection<ISourceContainer> resolveSourceContainers(File classesLocation, IProgressMonitor monitor)
            throws CoreException {

        var sourceJar = SourceJarFinder.findSourceJar(classesLocation.toPath());
        if (sourceJar != null) {
            return Set.of(new ExternalArchiveSourceContainer(sourceJar.toOSString(), true));
        }

        return null;
    }

}
