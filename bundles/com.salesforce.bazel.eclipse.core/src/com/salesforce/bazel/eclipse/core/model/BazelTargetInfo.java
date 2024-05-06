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

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.model.BazelProject.hasOwnerPropertySetForLabel;
import static com.salesforce.bazel.eclipse.core.model.BazelProject.hasWorkspaceRootPropertySetToLocation;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.RuleInternal;
import com.salesforce.bazel.sdk.model.TargetInternal;

public final class BazelTargetInfo extends BazelElementInfo {

    /**
     * Finds a {@link IProject} for a given target.
     * <p>
     * This method works without opening/loading the model.
     * </p>
     *
     * @param bazelTarget
     *            the target to find the project for
     * @return the found project (maybe <code>null</code>)
     * @throws CoreException
     */
    static IProject findProject(BazelTarget bazelTarget) throws CoreException {
        var workspaceRoot = bazelTarget.getBazelWorkspace().getLocation();
        // we don't care about the actual project name - we look for the property
        var projects = getEclipseWorkspaceRoot().getProjects();
        for (IProject project : projects) {
            if (project.isAccessible() // is open
                    && project.hasNature(BAZEL_NATURE_ID) // is a Bazel project
                    && hasWorkspaceRootPropertySetToLocation(project, workspaceRoot) // belongs to the workspace root
                    && hasOwnerPropertySetForLabel(project, bazelTarget.getLabel()) // represents the target
            ) {
                return project;
            }
        }

        return null;
    }

    private final BazelTarget bazelTarget;
    private final String targetName;
    private TargetInternal target;
    private volatile BazelProject bazelProject;
    private volatile BazelRuleAttributes ruleAttributes;

    private List<IPath> ruleOutput;
    private BazelVisibility visibility;

    public BazelTargetInfo(String targetName, BazelTarget bazelTarget) {
        this.targetName = targetName;
        this.bazelTarget = bazelTarget;
    }

    public BazelProject getBazelProject() throws CoreException {
        var cachedProject = bazelProject;
        if (cachedProject != null) {
            return cachedProject;
        }

        var project = findProject(getBazelTarget());
        if (project == null) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Unable to find project for Bazel target '%s' in the Eclipse workspace. Please check the workspace setup!",
                            getBazelTarget().getLabel())));
        }
        return bazelProject = new BazelProject(project, getBazelTarget().getModel());
    }

    public BazelTarget getBazelTarget() {
        return bazelTarget;
    }

    @Override
    public BazelTarget getOwner() {
        return bazelTarget;
    }

    RuleInternal getRule() throws CoreException {
        var target = getTarget();
        if (!target.hasRule()) {
            throw new CoreException(Status.error(format("Bazel target '%s' is not backed by a rule!", bazelTarget)));
        }
        return target.rule();
    }

    public BazelRuleAttributes getRuleAttributes() throws CoreException {
        var cachedRuleAttributes = ruleAttributes;
        if (cachedRuleAttributes != null) {
            return cachedRuleAttributes;
        }

        return ruleAttributes = new BazelRuleAttributes(getRule());
    }

    public List<IPath> getRuleOutput() throws CoreException {
        var cachedOutput = ruleOutput;
        if (cachedOutput != null) {
            return cachedOutput;
        }

        var ruleOutputList = getRule().ruleOutputList();
        if (ruleOutputList != null) {
            return ruleOutput = ruleOutputList.stream()
                    .map(BazelLabel::new)
                    .map(BazelLabel::getTargetName)
                    .map(IPath::forPosix)
                    .collect(toList());
        }

        return ruleOutput = List.of();
    }

    /**
     * @return the underlying <code>com.google.devtools.build.lib.query2.proto.proto2api.Build.Target</code>
     */
    TargetInternal getTarget() {
        // don't expose non com.salesforce.bazel.eclipse.core.model API widely (package visibility is ok)
        return requireNonNull(target, () -> "not loaded: " + targetName);
    }

    public String getTargetName() {
        return targetName;
    }

    public BazelVisibility getVisibility() throws CoreException {
        var cachedVisibility = visibility;
        if (cachedVisibility != null) {
            return cachedVisibility;
        }

        var visibilityValue = getRuleAttributes().getStringList("visibility");
        if ((visibilityValue == null) || visibilityValue.isEmpty()) {
            // lookup from package
            return visibility = getBazelTarget().getBazelPackage().getDefaultVisibility();
        }

        return visibility = new BazelVisibility(visibilityValue);
    }

    public void load(BazelPackageInfo packageInfo) throws CoreException {
        // re-use the info obtained from bazel query for the whole package
        var target = packageInfo.getTarget(getTargetName());
        if (target == null) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Target '%s' does not exist in package '%s'!",
                            getTargetName(),
                            packageInfo.getBazelPackage().getLabel())));
        }

        this.target = target;
    }
}
