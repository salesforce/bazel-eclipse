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
 * This class represents the <code>MODULE.bazel</code> file found inside a {@link BazelWorkspace}.
 * <p>
 * See <a href="https://bazel.build/concepts/build-ref">Workspaces, packages, and targets</a> in the Bazel documentation
 * for further details.
 * </p>
 */
public final class BazelModuleFile extends BazelFile<BazelModuleFileInfo, BazelWorkspace> {

    BazelModuleFile(BazelWorkspace bazelWorkspace, IPath buildFileLocation) {
        super(bazelWorkspace, IPath.EMPTY, buildFileLocation);
    }

    @Override
    protected BazelModuleFileInfo createInfo() throws CoreException {
        var reader = readBuildFile();
        return new BazelModuleFileInfo(
                this,
                reader.getLoadStatements(),
                reader.getMacroCalls(),
                reader.getModuleCall());
    }

    @Override
    public BazelWorkspace getBazelWorkspace() {
        return getParent();
    }
}
