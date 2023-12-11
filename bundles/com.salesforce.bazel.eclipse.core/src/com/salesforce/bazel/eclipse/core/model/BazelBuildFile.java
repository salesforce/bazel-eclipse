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
 *      Salesforce - initial implementation
*/
package com.salesforce.bazel.eclipse.core.model;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

/**
 * This class represents the <code>BUILD.bazel</code> or <code>BUILD</code> file found inside a {@link BazelPackage}.
 * <p>
 * Although technically not necessary for building Bazel projects when working with <code>bazel query</code> and
 * <code>bazel build</code>, the build file becomes very important for IDEs. Often <code>bazel query</code> tells you
 * how Bazel sees the world. This may be different from how a developers describes it. For example, with
 * <code>bazel query</code> you will see {@link BazelPackage packages} and {@link BazelTarget targets} but not
 * <a href="https://bazel.build/extending/macros">macros</a> and functions such as <code>glob</code>. Therefore this
 * class exist in the Bazel model to allow the IDEs to drill down into the build file and discovers those.
 * </p>
 * <p>
 * See <a href="https://bazel.build/concepts/build-ref">Workspaces, packages, and targets</a> in the Bazel documentation
 * for further details.
 * </p>
 */
public final class BazelBuildFile extends BazelFile<BazelBuildFileInfo, BazelPackage> {

    BazelBuildFile(BazelPackage bazelPackage, IPath buildFileLocation) {
        super(bazelPackage, bazelPackage.getWorkspaceRelativePath(), buildFileLocation);
    }

    @Override
    protected BazelBuildFileInfo createInfo() throws CoreException {
        var reader = readBuildFile();
        return new BazelBuildFileInfo(
                this,
                reader.getLoadStatements(),
                reader.getMacroCalls(),
                reader.getPackageCall());
    }

    @Override
    public BazelWorkspace getBazelWorkspace() {
        return getParent().getBazelWorkspace();
    }
}
