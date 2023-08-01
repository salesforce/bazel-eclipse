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
 *      Salesforce - Partially adapted and heavily inspired from Eclipse JDT, M2E and PDE
 */
package com.salesforce.bazel.eclipse.core.model;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * The information representing a {@link BazelElement} after it has been read/queried from Bazel.
 * <p>
 * Element infos are used by the model. SDK users should not access them directly. If they believe the have to please
 * open a discussion thread with the SDK team for lack of API in the {@link BazelElement} model.
 * </p>
 */
public abstract sealed class BazelElementInfo
        permits BazelWorkspaceInfo, BazelPackageInfo, BazelTargetInfo, BazelModelInfo, BazelBuildFileInfo {

    static IWorkspaceRoot getEclipseWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

}
