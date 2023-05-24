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

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * The Bazel model provides access to everything an Eclipse workspace knows about.
 * <p>
 * Eclipse has the concept of a singleton workspace {@link IWorkspace}. Bazel also uses the notion of workspaces. The
 * Bazel model in Eclipse supports working with multiple Bazel workspaces in a single Eclipse workspace. This allows
 * working on a set of changes spanning multiple Bazel workspaces using Bazel's <code>override-repository</code>
 * functionality.
 * </p>
 */
public final class BazelModel extends BazelElement<BazelModelInfo, BazelElement<?, ?>> {

    private final BazelModelManager modelManager;

    public BazelModel(BazelModelManager modelManager) {
        this.modelManager = modelManager;
    }

    @Override
    protected BazelModelInfo createInfo() throws CoreException {
        return new BazelModelInfo(this);
    }

    @Override
    public boolean equals(Object obj) {
        // all BazelModel objects are equal
        return (obj != null) && obj.getClass().equals(BazelModel.class);
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public BazelWorkspace getBazelWorkspace() {
        return null; // no workspace
    }

    /**
     * Returns the {@link BazelWorkspace} for the given workspace root location.
     * <p>
     * This is a handle-only method. The returns {@link BazelWorkspace} may not exists.
     * </p>
     *
     * @param workspaceRoot
     *            the workspace root location
     * @return the {@link BazelWorkspace} handle
     */
    public BazelWorkspace getBazelWorkspace(IPath workspaceRoot) {
        return new BazelWorkspace(workspaceRoot, this);
    }

    @Override
    public BazelLabel getLabel() {
        return null; // no label
    }

    @Override
    public IPath getLocation() {
        return null; // no location
    }

    /**
     * @return the owning model manager
     */
    public BazelModelManager getModelManager() {
        return modelManager;
    }

    @Override
    public BazelElement<?, ?> getParent() {
        return null;
    }

    /**
     * Returns the list of workspaces the model knows about.
     * <p>
     * A Bazel workspace is identified in the Eclipse workspace by traversing all projects and identifying those mapping
     * to the Bazel workspace. Each Bazel workspace is required to be represented by an {@link IProject} in Eclipse.
     * </p>
     *
     * @return the list of workspaces the model knows about
     * @throws CoreException
     */
    public List<BazelWorkspace> getWorkspaces() throws CoreException {
        return getInfo().findWorkspaces();
    }

    @Override
    public int hashCode() {
        return BazelModel.class.hashCode();
    }
}
