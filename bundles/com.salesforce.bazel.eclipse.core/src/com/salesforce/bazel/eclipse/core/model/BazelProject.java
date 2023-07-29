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
import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
 * assume such a project has a location which does not match the target or package it is representing. The
 * {@link BazelProjectFileSystemMapper} implements this detail.
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
     * containing the full qualified label of the package or target a project was created for
     * <p>
     * The property will be set on Bazel target and package projects.
     * </p>
     */
    public static final QualifiedName PROJECT_PROPERTY_OWNER = new QualifiedName(PLUGIN_ID, "owner");

    /**
     * A {@link IResource#getPersistentProperty(QualifiedName) persistent property} set on an {@link IProject}
     * containing the full qualified label of the targets represented by a package project.
     * <p>
     * The property may be set on Bazel package projects only.
     * </p>
     */
    public static final QualifiedName PROJECT_PROPERTY_TARGETS = new QualifiedName(PLUGIN_ID, "bazel_targets");

    /**
     * Attempts to recover the workspace root property value.
     * <p>
     * This method must only be called on Bazel projects. After an import in Eclipse (eg., importing existing projects)
     * the persistent property metadata might not be there. This method attempts to recover the property be scanning the
     * file system for a Bazel workspace.
     * </p>
     *
     * @param project
     * @return
     * @throws CoreException
     */
    private static String getOrFixWorkspaceRootPropertyValue(IProject project) throws CoreException {
        var workspaceRootPropertyValue = project.getPersistentProperty(PROJECT_PROPERTY_WORKSPACE_ROOT);
        if (workspaceRootPropertyValue != null) {
            return workspaceRootPropertyValue;
        }

        var location =
                requireNonNull(project.getLocation(), "Bazel projects must have a location in the local file system");
        while (true) {
            var workspaceFile = BazelWorkspace.findWorkspaceFile(location.toPath());
            if (workspaceFile != null) {
                project.setPersistentProperty(PROJECT_PROPERTY_WORKSPACE_ROOT, location.toString());
                return location.toString();
            }

            if (location.isRoot()) {
                // we search everything including the root, give up
                return null;
            }

            // continue with parent
            location = location.removeLastSegments(1);
        }
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
            throw new IllegalArgumentException(
                    format(
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
        var workspaceRootPropertyValue = getOrFixWorkspaceRootPropertyValue(project);
        // workspace root must be set and must match the project location
        return (workspaceRootPropertyValue != null) && (workspaceRoot != null)
                && workspaceRoot.toString().equals(workspaceRootPropertyValue);
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
        var other = (BazelProject) obj;
        return Objects.equals(bazelModel, other.bazelModel) && Objects.equals(project, other.project);
    }

    BazelModel getBazelModel() {
        return requireNonNull(bazelModel, "not properly initialized - missing Bazel element");
    }

    /**
     * Returns the {@link BazelPackage} this project belongs to.
     * <p>
     * The model will be searched for the package.
     * </p>
     * <p>
     * If the project does not represent a package, the method with fail and throw a {@link CoreException}.
     * </p>
     *
     * @return the target
     * @throws CoreException
     *             if the target project cannot be found or if this project does not represent a package
     */
    public BazelPackage getBazelPackage() throws CoreException {
        var label = getOwnerLabel();
        if ((label == null) || label.hasTarget()) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Project '%s' does not map to a Bazel packagage (owner label is '%s').",
                            project,
                            label)));
        }

        // search model
        var bazelWorkspace = getBazelWorkspace();
        var bazelPackage = bazelWorkspace.getBazelPackage(label.getPackageLabel());

        if (!bazelPackage.exists()) {
            throw new CoreException(
                    Status.error(
                        format("Project '%s' maps to a Bazel package '%s', which does not exist!", project, label)));
        }

        return bazelPackage;
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
        var label = getOwnerLabel();
        if ((label == null) || !label.hasTarget()) {
            throw new CoreException(
                    Status.error(
                        format("Project '%s' does not map to a Bazel target (owner label is '%s').", project, label)));
        }

        // search model
        var bazelWorkspace = getBazelWorkspace();
        var bazelPackage = bazelWorkspace.getBazelPackage(label.getPackageLabel());
        var bazelTarget = bazelPackage.getBazelTarget(label.getTargetName());

        if (!bazelTarget.exists()) {
            throw new CoreException(
                    Status.error(
                        format("Project '%s' maps to a Bazel target '%s', which does not exist!", project, label)));
        }

        return bazelTarget;
    }

    /**
     * Returns a list of target names represented by the package project.
     * <p>
     * If the project does not represent a package, the method with fail and throw a {@link CoreException}.
     * </p>
     *
     * @return a list of target names
     * @throws CoreException
     */
    public List<BazelTarget> getBazelTargets() throws CoreException {
        // ensure this is a package project
        var bazelPackage = getBazelPackage();

        // get targets list
        var targetsPropertyValue = getProject().getPersistentProperty(PROJECT_PROPERTY_TARGETS);
        if ((targetsPropertyValue == null) || targetsPropertyValue.isBlank()) {
            return Collections.emptyList();
        }

        List<BazelTarget> packageTargets = new ArrayList<>();
        for (String targetName : targetsPropertyValue.split(":")) {
            packageTargets.add(bazelPackage.getBazelTarget(targetName));
        }
        return packageTargets;
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

        var workspaceRoot = getOrFixWorkspaceRootPropertyValue(project);
        if (workspaceRoot == null) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Project '%s' is not a conformant Bazel project. No workspace root is set for this project!",
                            project)));
        }

        // search model
        for (BazelWorkspace workspace : getBazelModel().getWorkspaces()) {
            if (workspace.getLocation().toString().equals(workspaceRoot)) {
                return workspace;
            }
        }

        throw new CoreException(
                Status.error(
                    format(
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

    /**
     * {@return convenience for <code>getProject().getName()</code>}
     */
    public String getName() {
        return getProject().getName();
    }

    /**
     * Returns the label of the owning Bazel element of this project.
     * <p>
     * The owner is either a {@link BazelPackage} or a {@link BazelLabel}. Callers can use
     * {@link BazelLabel#hasTarget()} to check.
     * </p>
     *
     * @return the label of the owner of this project (maybe <code>null</code> if this is the workspace project)
     * @throws CoreException
     *             if the project does not exist
     */
    public BazelLabel getOwnerLabel() throws CoreException {
        var project = getProject();
        var ownerPropertyValue = project.getPersistentProperty(PROJECT_PROPERTY_OWNER);
        return ownerPropertyValue != null ? new BazelLabel(ownerPropertyValue) : null;
    }

    @Override
    public IProject getProject() {
        return requireNonNull(project, "not properly initialized");
    }

    @Override
    public int hashCode() {
        return Objects.hash(bazelModel, project);
    }

    /**
     * Indicates if this project represents a single {@link BazelPackage}.
     *
     * @return <code>true</code> if this project is a package project, <code>false</code> otherwise
     * @throws CoreException
     */
    public boolean isPackageProject() throws CoreException {
        var label = getOwnerLabel();
        return (label != null) && !label.hasTarget();
    }

    /**
     * Indicates if this project represents a single {@link BazelTarget}.
     *
     * @return <code>true</code> if this project is a target project, <code>false</code> otherwise
     * @throws CoreException
     */
    public boolean isTargetProject() throws CoreException {
        var label = getOwnerLabel();
        return (label != null) && label.hasTarget();
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

    public void setBazelTargets(List<BazelTarget> targets) throws CoreException {
        var bazelPackage = getBazelPackage();
        List<String> targetNames = new ArrayList<>();
        for (BazelTarget bazelTarget : targets) {
            if (!bazelPackage.equals(bazelTarget.getBazelPackage())) {
                throw new IllegalArgumentException(
                        format(
                            "This method should only be called with targets belonging to package '%s'. Got '%s'",
                            bazelPackage,
                            bazelTarget));
            }
            targetNames.add(bazelTarget.getTargetName());
        }

        getProject().setPersistentProperty(PROJECT_PROPERTY_TARGETS, targetNames.stream().collect(joining(":")));
    }

    public void setModel(BazelModel bazelModel) {
        this.bazelModel = requireNonNull(bazelModel, "the model should never be set to null");
    }

    @Override
    public void setProject(IProject project) {
        this.project = requireNonNull(project, "the project should never be set to null");
    }

    @Override
    public String toString() {
        var result = new StringBuilder();
        result.append("BazelProject [");
        result.append(project);
        try {
            result.append(", workspace=");
            result.append(project.getPersistentProperty(PROJECT_PROPERTY_WORKSPACE_ROOT));
            result.append(", owner=");
            result.append(project.getPersistentProperty(PROJECT_PROPERTY_OWNER));
        } catch (CoreException e) {
            result.append(e);
        }
        result.append("]");
        return result.toString();
    }
}
