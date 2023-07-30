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

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_DOT_BAZELPROJECT;
import static java.util.Objects.requireNonNull;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.IPath;

/**
 * A mapper for virtual {@link BazelProject Bazel projects} from the Eclipse workspace into the actual file system.
 * <p>
 * As described in {@link BazelProject} the Bazel Eclipse Feature is very opinionated about how projects will be created
 * for Bazel workspaces. This mapper helps with projects representing targets. Those projects cannot be created directly
 * within the folder of ther package due to potential conflicts. Instead we create all those projects in a dedicated
 * project area and use links/virtual resource in Eclipse to point to the actual resources.
 * </p>
 * <p>
 * There are a few constraints when using virtual resources especially with regards to source control
 * plug-ins/extension. This mapper tries to address some of them. For example, by placing the project area within a
 * <code>.eclipse/projects</code> folder insider the Bazel workspace it should be possible to share/version those
 * project definitions. However, this is a design assumption which needs to be verified and revisited once we have more
 * experience at scale.
 * </p>
 * <p>
 * Note, callers of this class must ensure their usage allows the return value of any location in this class to
 * change/evolve over time. This specifically means that locations should only be used/queried at creation/provisioning
 * time of projects. It should not be used for computing locations and discovery during day to day use. Instead other
 * means of the Bazel model must be used.
 * </p>
 */
public class BazelProjectFileSystemMapper {

    private static final String DOT_ECLIPSE_FOLDER = ".eclipse";
    private final BazelWorkspace bazelWorkspace;

    /**
     * Creates a new mapper instance for a Bazel workspace.
     *
     * @param bazelWorkspace
     *            the Bazel workspace
     */
    public BazelProjectFileSystemMapper(BazelWorkspace bazelWorkspace) {
        this.bazelWorkspace = requireNonNull(bazelWorkspace, "no workspace specified");
    }

    public BazelWorkspace getBazelWorkspace() {
        return bazelWorkspace;
    }

    /**
     * @see #getVirtualSourceFolder(BazelProject)
     */
    public IFolder getOutputFolder(BazelProject project) {
        return project.getProject().getFolder("eclipse-bin");
    }

    /**
     * @see #getVirtualSourceFolderForTests(BazelProject)
     */
    public IFolder getOutputFolderForTests(BazelProject project) {
        return project.getProject().getFolder("eclipse-testbin");
    }

    /**
     * Returns the absolute location in the file system where projects for none workspace projects shall be created.
     *
     * @return the absolute location in the file system (never <code>null</code>)
     */
    public IPath getProjectsArea() {
        return getBazelWorkspace().getLocation().append(DOT_ECLIPSE_FOLDER).append("projects");
    }

    /**
     * {@return the absolute path in the file system to the <code>.bazelproject</code> file used by the workspace}
     */
    public IPath getProjectViewLocation() {
        return getBazelWorkspace().getLocation().append(DOT_ECLIPSE_FOLDER).append(FILE_NAME_DOT_BAZELPROJECT);
    }

    /**
     * @see #getVirtualSourceFolder(BazelProject)
     */
    public IFolder getVirtualResourceFolder(BazelProject project) {
        return project.getProject().getFolder("resources");
    }

    /**
     * @see #getVirtualSourceFolderForTests(BazelProject)
     */
    public IFolder getVirtualResourceFolderForTests(BazelProject project) {
        return project.getProject().getFolder("test-resources");
    }

    /**
     * Returns the folder where source files links to sources will be created.
     * <p>
     * This can be used for very narrow scoped projects, i.e. projects representing a single target. For projects which
     * combines multiple targets this method should not be used to avoid confusion.
     * </p>
     *
     * @param project
     *            the Bazel project
     * @return the virtual source folder
     */
    public IFolder getVirtualSourceFolder(BazelProject project) {
        return project.getProject().getFolder("srcs");
    }

    /**
     * Returns the folder where source files links to test sources will be created.
     * <p>
     * This can be used for very narrow scoped projects, i.e. projects representing a single target. For projects which
     * combines multiple targets this method should not be used to avoid confusion.
     * </p>
     *
     * @param project
     *            the Bazel project
     * @return the virtual test source folder
     */
    public IFolder getVirtualSourceFolderForTests(BazelProject project) {
        return project.getProject().getFolder("test-srcs");
    }
}
