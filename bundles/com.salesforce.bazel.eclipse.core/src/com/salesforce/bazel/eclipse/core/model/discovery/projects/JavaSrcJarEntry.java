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
package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import org.eclipse.core.runtime.IPath;

/**
 * An entry inside a <code>.srcjar</code>.
 * <p>
 * In contrast to a standard {@link JavaSourceEntry} this specialization overrides the contract of
 * {@link #getPotentialSourceDirectoryRoot()} to be an absolute path to a directory somewhere on the file system.
 * Additional, the {@link #getBazelPackageLocation()} is also not really a Bazel package but the directory of the
 * explosed srcjar.
 * </p>
 */
public class JavaSrcJarEntry extends JavaSourceEntry {

    public JavaSrcJarEntry(IPath relativePath, IPath srcJarDirectory) {
        super(relativePath, srcJarDirectory);
    }

    /**
     * {@retun the location to the exploded srcjar}
     */
    @Override
    public IPath getBazelPackageLocation() {
        return super.getBazelPackageLocation();
    }

    /**
     * @return first few segments of {@link #getPathParent()} (within {@link #getSrcJarDirectoryLocation()}) which could
     *         be the source directory, or <code>null</code> if unlikely
     */
    @Override
    public IPath getPotentialSourceDirectoryRoot() {
        var detectedPackagePath = getDetectedPackagePath();

        // note, we check the full path because we *want* to identify files from targets defined within a Java package
        var absolutePathToJavaFileDirectory = getSrcJarDirectoryLocation().append(getPathParent());
        if (endsWith(absolutePathToJavaFileDirectory, detectedPackagePath)) {
            return absolutePathToJavaFileDirectory.removeLastSegments(detectedPackagePath.segmentCount());
        }

        return getSrcJarDirectoryLocation(); // assume srcjar directory
    }

    /**
     * {@retun the location to the exploded srcjar}
     */
    public IPath getSrcJarDirectoryLocation() {
        // the package location is the src jar directory
        return getBazelPackageLocation();
    }

    @Override
    public boolean isExternalOrGenerated() {
        return true;
    }
}
