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

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_BUILDER_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.PLUGIN_ID;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.projectview.BazelProjectView;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * A Bazel project is a specialization of an {@link IProject} which has a Bazel project nature attached.
 * <p>
 * The {@link BazelProject} is intentionally not part of the Bazel model. This is primarily because Bazel does not have
 * the notion of a project. Projects are an IDE specific artifact. The Bazel Eclipse plug-in differentiates two types of
 * Bazel projects - one for the Bazel workspace itself and one (or multiple) for the targets/packages made visible in
 * Eclipse.
 * </p>
 * <p>
 * The {@link BazelWorkspace Bazel workspace project} represents the project, which points directly to the Bazel
 * workspace. This project use the {@link BazelWorkspace#getLocation() workspace location as the project directory}. It
 * intentionally does this so IDEs can present users with a full view into the Bazel workspace. By design Bazel
 * workspace projects must never exists outside a Bazel workspace or point to any file system location other than a
 * workspace root, i.e. the following condition is always <code>true</code>:
 * <code>IProject#getLocation().equals(BazelWorkspace#getLocation())</code>.
 * </p>
 * <p>
 * A {@link BazelPackage Bazel package project} or a {@link BazelTarget Bazel target projects} represent one or multiple
 * targets according to the {@link BazelProjectView Bazel project view} configured for a Bazel workspace. Those projects
 * will be virtual, i.e. their location maybe physically within a Bazel workspace or outside/external (eg., in the
 * Eclipse workspace area). This is an implementation detail which clients must take into account, i.e. we never must
 * assume such a project has a location which does not match the target or package it is representing.
 * </p>
 */
public class BazelProject implements IProjectNature {

    /**
     * A {@link IResource#getPersistentProperty(QualifiedName) persistent property} set on an {@link IProject}
     * representing the workspace root directory.
     * <p>
     * The property will be set on any Bazel project, i.e. workspace and package/target projects.
     * </p>
     */
    public static final QualifiedName PROJECT_PROPERTY_WORKSPACE_ROOT = new QualifiedName(PLUGIN_ID, "workspace_root");

    /**
     * A {@link IResource#getPersistentProperty(QualifiedName) persistent property} set on an {@link IProject}
     * containing full qualified labels of all targets represented by a project.
     * <p>
     * The property will be set on Bazel target and package projects. The value is a list of labels separated by comma.
     * </p>
     */
    public static final QualifiedName PROJECT_PROPERTY_TARGETS = new QualifiedName(PLUGIN_ID, "targets");

    /**
     * A {@link IResource#getPersistentProperty(QualifiedName) persistent property} set on an {@link IProject}
     * containing the full qualified label of the package or target a project was created for
     * <p>
     * The property will be set on Bazel target and package projects.
     * </p>
     */
    public static final QualifiedName PROJECT_PROPERTY_OWNER = new QualifiedName(PLUGIN_ID, "owner");

    private static Stream<String> getLabels(String popertyValue) {
        return Stream.of(popertyValue.trim().split("\\s*,\\s*"));
    }

    /**
     * A convenience method for checking if a project has the {@link #PROJECT_PROPERTY_OWNER} set to the given label.
     *
     * @param project
     *            the project to check
     * @param label
     *            the expected label
     * @return <code>true</code> if the property is set and contain the label
     * @throws CoreException
     *             if the project is closed
     */
    public static boolean hasOwnerPropertySetForLabel(IProject project, BazelLabel label) throws CoreException {
        // prevent non-concrete and external repo labels
        if (!label.isConcrete() || label.isExternalRepoLabel()) {
            throw new IllegalArgumentException(format(
                "Invalid label '%s': Label must be concrete target or a package and without external repo identifier",
                label));
        }

        var ownerValue = project.getPersistentProperty(PROJECT_PROPERTY_OWNER);
        if ((ownerValue == null) || ownerValue.isBlank()) {
            return false;
        }

        var labelString = label.getLabelPath();
        return ownerValue.equals(labelString);
    }

    /**
     * A convenience method for checking if a project has the {@link #PROJECT_PROPERTY_WORKSPACE_ROOT} set to the given
     * location.
     *
     * @param project
     *            the project to check
     * @param workspaceRoot
     *            the expected location
     * @return <code>true</code> if the property is set and matched the location
     * @throws CoreException
     *             if the project is closed
     */
    public static boolean hasWorkspaceRootPropertySetToLocation(IProject project, IPath workspaceRoot)
            throws CoreException {
        var workspaceRootPropertyValue = project.getPersistentProperty(PROJECT_PROPERTY_WORKSPACE_ROOT);
        // workspace root must be set and must match the project location
        return (workspaceRootPropertyValue != null) && (workspaceRoot != null)
                && workspaceRoot.toString().equals(workspaceRootPropertyValue);
    }

    /**
     * A convenience method for checking how many targets a project represents.
     *
     * @param project
     *            the project to check
     * @return the number of targets
     * @throws CoreException
     *             if the project is closed
     */
    public static int numberOfTargetsinTargetProperty(IProject project) throws CoreException {
        var targetsPropertyValue = project.getPersistentProperty(PROJECT_PROPERTY_TARGETS);
        if ((targetsPropertyValue == null) || targetsPropertyValue.isBlank()) {
            return 0;
        }

        var result = getLabels(targetsPropertyValue).count();
        if (result > Integer.MAX_VALUE) {
            throw new CoreException(Status.error(format("Error in property  '%s' on project '%s'. Too many targets: %s",
                PROJECT_PROPERTY_TARGETS, project, targetsPropertyValue)));
        }

        return (int) result;
    }

    private IProject project;
    private BazelModel bazelModel;

    /**
     * The default constructor is used by Eclipse when the nature is configured for an {@link IProject}.
     * <p>
     * No one should create a project directly but prefer the {@link BazelCore#create(IProject)} method (or via the
     * model manager)
     * </p>
     */
    public BazelProject() {
        // constructor for Eclipse (when the nature is configured)
    }

    public BazelProject(IProject project, BazelModel model) {
        setProject(project);
        setModel(model);
    }

    boolean addBazelBuilder(IProject project, IProjectDescription description, IProgressMonitor monitor)
            throws CoreException {
        var setProjectDescription = false;
        if (description == null) {
            description = project.getDescription();
            setProjectDescription = true;
        }

        // ensure Maven builder is always the last one
        ICommand bazelBuilder = null;
        List<ICommand> newSpec = new ArrayList<>();
        var i = 0;
        for (ICommand command : description.getBuildSpec()) {
            if (BAZEL_BUILDER_ID.equals(command.getBuilderName())) {
                bazelBuilder = command;
                if (i == (description.getBuildSpec().length - 1)) {
                    // This is the maven builder command and it is the last one in the list - there is nothing to change
                    return false;
                }
            } else {
                newSpec.add(command);
            }
            i++;
        }
        if (bazelBuilder == null) {
            bazelBuilder = description.newCommand();
            bazelBuilder.setBuilderName(BAZEL_BUILDER_ID);
        }
        newSpec.add(bazelBuilder);
        description.setBuildSpec(newSpec.toArray(ICommand[]::new));

        if (setProjectDescription) {
            project.setDescription(description, monitor);
        }
        return true;
    }

    @Override
    public void configure() throws CoreException {
        addBazelBuilder(project, project.getDescription(), null);
    }

    @Override
    public void deconfigure() throws CoreException {
        removeBazelBuilder(project, project.getDescription(), null);
    }

    BazelModel getBazelModel() {
        return requireNonNull(bazelModel, "not properly initialized - missing Bazel element");
    }

    /**
     * Returns the {@link BazelTarget} this project belongs to.
     * <p>
     * The model will be searched for the target.
     * </p>
     * <p>
     * If the project does not represent a single target, the method with fail and throw a {@link CoreException}.
     * </p>
     *
     * @return the target
     * @throws CoreException
     *             if the target project cannot be found or if this project does not represent a single target
     */
    public BazelTarget getBazelTarget() throws CoreException {
        var project = getProject();
        var ownerPropertyValue = project.getPersistentProperty(PROJECT_PROPERTY_OWNER);
        if ((ownerPropertyValue == null) || ownerPropertyValue.isEmpty()) {
            throw new CoreException(Status.error(format("Project '%s' is not owned by a Bazel element.", project)));
        }

        var label = new BazelLabel(ownerPropertyValue);
        if (!label.hasTarget()) {
            throw new CoreException(
                    Status.error(format("Project '%s' does not map to a Bazel target but to '%s'.", project, label)));
        }

        // search model
        var bazelWorkspace = getBazelWorkspace();
        var bazelPackage = bazelWorkspace.getBazelPackage(label.getPackageLabel());
        var bazelTarget = bazelPackage.getBazelTarget(label.getTargetName());

        if (!bazelTarget.exists()) {
            throw new CoreException(Status
                    .error(format("Project '%s' maps to a Bazel target '%s', which does not exist!", project, label)));
        }

        return bazelTarget;
    }

    /**
     * Returns the {@link BazelWorkspace} this project belongs to.
     * <p>
     * The model will be searched for the workspace.
     * </p>
     *
     * @return the workspace
     * @throws CoreException
     *             if the workspace project cannot be found
     */
    public BazelWorkspace getBazelWorkspace() throws CoreException {
        var project = getProject();

        var workspaceRoot = project.getPersistentProperty(PROJECT_PROPERTY_WORKSPACE_ROOT);
        if (workspaceRoot == null) {
            throw new CoreException(Status.error(
                format("Project '%s' is not a conformant Bazel project. No workspace root is set for this project!",
                    project)));
        }

        // search model
        for (BazelWorkspace workspace : getBazelModel().getWorkspaces()) {
            if (workspace.getLocation().toString().equals(workspaceRoot)) {
                return workspace;
            }
        }

        throw new CoreException(Status.error(format(
            "Unable to find Bazel workspace for workspace root '%s' in the Eclipse workspace. Please check the workspace setup!",
            workspaceRoot)));
    }

    /**
     * Returns the location of the project.
     * <p>
     * The returned location will be the Bazel workspace root directory f {@link #isWorkspaceProject()} returns
     * <code>true</code>, i.e. this is a workspace project.
     * </p>
     * <p>
     * Because the Bazel plug-in does not support {@link IProject projects} without a location, this method will never
     * return <code>null</code>.
     * </p>
     *
     * @return the location of the project (never <code>null</code>)
     */
    public IPath getLocation() {
        return requireNonNull(getProject().getLocation(), "unsupported project: getLocation() returned null");
    }

    @Override
    public IProject getProject() {
        return requireNonNull(project, "not properly initialized");
    }

    /**
     * Indicates if this project represents a single Bazel Target.
     *
     * @return <code>true</code> if this project is a target project, <code>false</code> otherwise
     * @throws CoreException
     */
    public boolean isSingleTargetProject() throws CoreException {
        return numberOfTargetsinTargetProperty(getProject()) == 1;
    }

    /**
     * Indicates if this project represents a Bazel Workspace.
     *
     * @return <code>true</code> if this project is a workspace project, <code>false</code> otherwise
     * @throws CoreException
     */
    public boolean isWorkspaceProject() throws CoreException {
        var project = getProject();
        var workspaceRoot = project.getLocation();
        return hasWorkspaceRootPropertySetToLocation(project, workspaceRoot);
    }

    boolean removeBazelBuilder(IProject project, IProjectDescription description, IProgressMonitor monitor)
            throws CoreException {
        var setProjectDescription = false;
        if (description == null) {
            description = project.getDescription();
            setProjectDescription = true;
        }

        var foundBazelBuilder = false;
        List<ICommand> newSpec = new ArrayList<>();
        for (ICommand command : description.getBuildSpec()) {
            if (!BAZEL_BUILDER_ID.equals(command.getBuilderName())) {
                newSpec.add(command);
            } else {
                foundBazelBuilder = true;
            }
        }
        if (!foundBazelBuilder) {
            return false;
        }
        description.setBuildSpec(newSpec.toArray(ICommand[]::new));

        if (setProjectDescription) {
            project.setDescription(description, monitor);
        }

        return true;
    }

    public void setModel(BazelModel bazelModel) {
        this.bazelModel = requireNonNull(bazelModel, "the model should never be set to null");
    }

    @Override
    public void setProject(IProject project) {
        this.project = requireNonNull(project, "the project should never be set to null");
    }
}
