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
package com.salesforce.bazel.eclipse.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;

import com.salesforce.bazel.eclipse.core.model.BazelModel;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * The main entry point into the Bazel Eclipse headless extension.
 * <p>
 * This class provides mostly convenience methods for accessing and working with the Bazel model.
 * <p>
 */
public class BazelCore {

    /**
     * Returns a {@link BazelProject} for a given {@link IProject}.
     * <p>
     * Callers should check that the project has the proper Bazel project nature attached. Otherwise the resulting
     * behavior is undefined.
     * </p>
     *
     * @param project
     *            the Eclipse project
     * @return the {@link BazelProject}
     */
    public static BazelProject create(IProject project) {
        return BazelCorePlugin.getInstance().getBazelModelManager().getBazelProject(project);
    }

    /**
     * Returns a new {@link BazelWorkspace} handle for a given workspace root location.
     * <p>
     * This is a handle only method. The returns workspace may not exist.
     * </p>
     *
     * @param workspaceRoot
     *            the workspace root.
     * @return
     */
    public static BazelWorkspace createWorkspace(IPath workspaceRoot) {
        return BazelCorePlugin.getInstance().getBazelModelManager().getModel().getBazelWorkspace(workspaceRoot);
    }

    /**
     * Provides access to the {@link BazelModel} for the Eclipse workspace.
     *
     * @return the shared {@link BazelModel} instance
     */
    public static final BazelModel getModel() {
        return BazelCorePlugin.getInstance().getBazelModelManager().getModel();
    }
}
