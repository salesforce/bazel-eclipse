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

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_BUILD;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_BUILD_BAZEL;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE_BAZEL;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_WORKSPACE_BZLMOD;
import static java.lang.String.format;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.eclipse.core.projectview.BazelProjectView;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * This class represents a Bazel Workspace.
 * <p>
 * A workspace is defined by a folder having a <code>WORKSPACE</code>, <code>WORKSPACE.bazel</code> or
 * <code>WORKSPACE.bzlmod</code> file.
 * </p>
 * <p>
 * See <a href="https://bazel.build/concepts/build-ref">Workspaces, packages, and targets</a> in the Bazel documentation
 * for further details.
 * </p>
 * <p>
 * Important note: no canonical path resolution happens within the Bazel model. If the workspace directory is a symlink
 * the odel honors that. Workspaces are identified by the given path not their canonical path.
 * </p>
 */
public final class BazelWorkspace extends BazelElement<BazelWorkspaceInfo, BazelModel> {

    /**
     * Utility function to find a <code>WORKSPACE.bazel</code>, <code>WORKSPACE</code> or <code>WORKSPACE.bzlmod</code>
     * file in a directory.
     * <p>
     * This method is used by {@link BazelWorkspace#exists()} to determine whether a workspace exists.
     * </p>
     *
     * @param path
     *            the path to check
     * @return the found workspace file (maybe <code>null</code> if none exist)
     * @see #isWorkspaceFileName(String)
     */
    public static Path findWorkspaceFile(Path path) {
        for (String workspaceFile : List.of( //
            FILE_NAME_WORKSPACE_BAZEL, // prefer WORKSPACE.bazel
            FILE_NAME_WORKSPACE, // then WORKSPACE
            FILE_NAME_WORKSPACE_BZLMOD /* bzlmod is last */)) {
            var workspaceFilePath = path.resolve(workspaceFile);
            if (isRegularFile(workspaceFilePath)) {
                return workspaceFilePath;
            }
        }
        return null;
    }

    /**
     * Utility function to check whether a given file name is <code>WORKSPACE.bazel</code>, <code>WORKSPACE</code> or
     * <code>WORKSPACE.bzlmod</code>.
     *
     * @param fileName
     *            the file name to check
     * @return <code>true</code> if the file name is either <code>WORKSPACE.bazel</code>, <code>WORKSPACE</code> or
     *         <code>WORKSPACE.bzlmod</code>, <code>false</code> otherwise
     * @see #findWorkspaceFile(Path)
     */
    public static boolean isWorkspaceFileName(String fileName) {
        for (String packageFile : List.of(FILE_NAME_BUILD_BAZEL, FILE_NAME_BUILD)) {
            if (packageFile.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private final IPath root;

    private final BazelModel parent;

    /**
     * Creates a new Bazel Workspace at the given path.
     *
     * @param root
     *            absolute path to the workspace root directory
     * @param parent
     *            the model owning the workspace
     */
    public BazelWorkspace(IPath root, BazelModel parent) throws NullPointerException, IllegalArgumentException {
        this.parent = requireNonNull(parent, "No model provided!");
        if (!requireNonNull(root, "No workspace root provided!").isAbsolute()) {
            throw new IllegalArgumentException("The path to the workspace root directory must be absolute!");
        }
        this.root = root;
    }

    private void checkIsRootedAtThisWorkspace(BazelLabel label) {
        if (!isRootedAtThisWorkspace(label)) {
            throw new IllegalArgumentException(format("Label '%s' is not rooted at workspace '%s'.", label, getName()));
        }
    }

    @Override
    protected BazelWorkspaceInfo createInfo() throws CoreException {
        var workspaceFile = findWorkspaceFile(workspacePath());
        if (workspaceFile == null) {
            throw new CoreException(Status.error(format("Workspace '%s' does not exist!", root.toString())));
        }

        var info = new BazelWorkspaceInfo(root, workspaceFile, this);
        info.load(getModelManager().getExecutionService());
        return info;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        var other = (BazelWorkspace) obj;
        return Objects.equals(parent, other.parent) && Objects.equals(root, other.root);
    }

    @Override
    public boolean exists() {
        var path = workspacePath();
        return isDirectory(path) && (findWorkspaceFile(path) != null);
    }

    /**
     * Returns a Bazel package for the given label.
     * <p>
     * This is a handle-only method. The underlying package may or may not exist in the workspace.
     * </p>
     *
     * @param label
     *            label of a target or package (must not be <code>null</code>)
     * @return the {@link BazelPackage Bazel package handle} (never <code>null</code>)
     * @throws IllegalArgumentException
     *             if the label is rooted at a different workspace
     */
    public BazelPackage getBazelPackage(BazelLabel label) {
        checkIsRootedAtThisWorkspace(label);
        return getBazelPackage(new org.eclipse.core.runtime.Path(label.getPackagePath()));
    }

    /**
     * Returns a Bazel package for the given path, which must be a folder representing the Bazel package hierarchy.
     * <p>
     * This is a handle-only method. The underlying package may or may not exist in the workspace.
     * </p>
     * <p>
     * The specified path can be absolute or relative. It will be resolved relative to the workspace root folder.
     * </p>
     *
     * @param path
     *            path to the folder (must not be <code>null</code>)
     * @return the {@link BazelPackage Bazel package handle} (never <code>null</code>)
     */
    public BazelPackage getBazelPackage(IPath path) {
        return new BazelPackage(this, path);
    }

    /**
     * The {@link BazelProject Bazel workspace project} for this workspace
     *
     * @return the Bazel workspace project
     * @throws CoreException
     *             if the project cannot be found in the Eclipse workspace
     */
    public BazelProject getBazelProject() throws CoreException {
        return getInfo().getBazelProject();
    }

    /**
     * {@return the {@link BazelProjectFileSystemMapper} for this workspace}
     *
     * @throws CoreException
     *             if the project cannot be found in the Eclipse workspace
     */
    public BazelProjectFileSystemMapper getBazelProjectFileSystemMapper() throws CoreException {
        return getInfo().getBazelProjectFileSystemMapper();
    }

    /**
     * Returns the project view for this workspace.
     * <p>
     * There is a single project view file used by the workspace at
     * {@link BazelProjectFileSystemMapper#getProjectViewLocation() a defined location}. This method reads the file and
     * returns the parsed project view.
     * </p>
     *
     * @return the project view for this workspace (never <code>null</code>)
     * @throws CoreException
     *             if no project view is available for the workspace
     */
    public BazelProjectView getBazelProjectView() throws CoreException {
        return getInfo().getBazelProjectView();
    }

    /**
     * Returns a {@link BazelTarget} handle for a given label.
     * <p>
     * If the label points to a package, the default target will be returned. Per Bazel documentation, the default
     * target has an unqualified name matching the last component of the package path.
     * </p>
     * <p>
     * This method is a handle-only method. The target may not may not exist in the workspace.
     * </p>
     *
     * @param label
     *            label of the target (must not be <code>null</code>)
     * @return the {@link BazelTarget Bazel target handle} (never <code>null</code>)
     * @throws IllegalArgumentException
     *             if the label is rooted at a different workspace
     */
    public BazelTarget getBazelTarget(BazelLabel label) {
        checkIsRootedAtThisWorkspace(label);
        return getBazelPackage(label).getBazelTarget(label.getTargetName());
    }

    /**
     * {@return the Bazel version used by this workspace}
     *
     * @throws CoreException
     *             if the workspace does not exist
     */
    public BazelVersion getBazelVersion() throws CoreException {
        return getInfo().getBazelVersion();
    }

    @Override
    public BazelWorkspace getBazelWorkspace() {
        return this;
    }

    @Override
    public BazelLabel getLabel() {
        // FIXME: the workspace should have a label but which one? @, @//, @name?
        return null;
    }

    @Override
    public IPath getLocation() {
        return root;
    }

    BazelModelManager getModelManager() {
        return parent.getModelManager();
    }

    @Override
    public String getName() {
        try {
            return getInfo().getWorkspaceName();
        } catch (CoreException e) {
            throw new IllegalStateException(format("Unable to compute workspace name for workspace '%s'!", root), e);
        }
    }

    @Override
    public BazelModel getParent() {
        return parent;
    }

    /**
     * {@return the absolute path to the WORKSPACE file used by this workspace}
     *
     * @throws CoreException
     *             if the workspace does not exist
     */
    public Path getWorkspaceFile() throws CoreException {
        return getInfo().getWorkspaceFile();
    }

    /**
     * {@return the absolute location to the WORKSPACE file used by this workspace}
     *
     * @throws CoreException
     *             if the workspace does not exist
     */
    public IPath getWorkspaceFileLocation() throws CoreException {
        return getLocation().append(getInfo().getWorkspaceFile().getFileName().toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, root);
    }

    /**
     * Indicates if a label is rooted at this workspace.
     * <p>
     * A label is rooted at this workspace if it has no repository name set (starts with <code>'//'</code>) or the
     * repository name matches {@link #getName() this workspace name}.
     * </p>
     *
     * @param label
     *            the label to check (must not be <code>null</code>)
     * @return <code>true</code> if the label is rooted at this workspace, <code>false</code> otherwise
     */
    public boolean isRootedAtThisWorkspace(BazelLabel label) {
        var externalRepositoryName = requireNonNull(label, "No label specified!").getExternalRepositoryName();
        if ((externalRepositoryName == null) || externalRepositoryName.isBlank()) {
            // all good
            return true;
        }

        return getName().equals(externalRepositoryName);
    }

    Path workspacePath() {
        return root.toFile().toPath();
    }
}
