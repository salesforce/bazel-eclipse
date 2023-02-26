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
import static com.salesforce.bazel.eclipse.core.model.BazelProject.hasTargetPropertySetForLabel;
import static com.salesforce.bazel.eclipse.core.model.BazelProject.hasWorkspaceRootPropertySetToLocation;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;

public final class BazelTargetInfo extends BazelElementInfo {

    private final BazelTarget bazelTarget;
    private final String targetName;
    private Target target;
    private volatile BazelProject bazelProject;
    private volatile BazelRuleAttributes ruleAttributes;

    public BazelTargetInfo(String targetName, BazelTarget bazelTarget) {
        this.targetName = targetName;
        this.bazelTarget = bazelTarget;
    }

    IProject findProject() throws CoreException {
        var workspaceProject = getBazelTarget().getBazelWorkspace().getBazelProject().getProject();
        var workspaceRoot = getBazelTarget().getBazelWorkspace().getLocation();
        // we don't care about the actual project name - we look for the property
        var projects = getEclipseWorkspaceRoot().getProjects();
        for (IProject project : projects) {
            if (project.hasNature(BAZEL_NATURE_ID) // is a Bazel project
                    && !workspaceProject.equals(project) // is not the workspace project
                    && hasWorkspaceRootPropertySetToLocation(project, workspaceRoot) // belongs to the workspace root
                    && hasTargetPropertySetForLabel(project, getBazelTarget().getLabel()) // represents the target
            ) {
                return project;
            }
        }

        return null;
    }

    public BazelProject getBazelProject() throws CoreException {
        var cachedProject = bazelProject;
        if (cachedProject != null) {
            return cachedProject;
        }

        var project = findProject();
        if (project == null) {
            throw new CoreException(Status.error(format(
                "Unable to find project for Bazel target '%s' in the Eclipse workspace. Please check the workspace setup!",
                getBazelTarget().getLabel())));
        }
        return bazelProject = new BazelProject(project, getBazelTarget().getModel());
    }

    public BazelTarget getBazelTarget() {
        return bazelTarget;
    }

    IWorkspaceRoot getEclipseWorkspaceRoot() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    public BazelRuleAttributes getRuleAttributes() throws CoreException {
        var cachedRuleAttributes = ruleAttributes;
        if (cachedRuleAttributes != null) {
            return cachedRuleAttributes;
        }

        var target = getTarget();
        if (!target.hasRule()) {
            throw new CoreException(Status.error(format("Bazel target '%s' is not backed by a rule!", bazelTarget)));
        }

        return ruleAttributes = new BazelRuleAttributes(target.getRule());
    }

    /**
     * @return the underlying <code>com.google.devtools.build.lib.query2.proto.proto2api.Build.Target</code>
     */
    Target getTarget() {
        // don't expose non com.salesforce.bazel.eclipse.core.model API widely (package visibility is ok)
        return requireNonNull(target, () -> "not loaded: " + targetName);
    }

    public String getTargetName() {
        return targetName;
    }

    public void load(BazelPackageInfo packageInfo) throws CoreException {
        // re-use the info obtained from bazel query for the whole package
        var target = packageInfo.getTarget(getTargetName());
        if (target == null) {
            throw new CoreException(Status.error(format("Target '%s' does not exist in package '%s'!", getTargetName(),
                packageInfo.getBazelPackage().getLabel())));
        }

        this.target = target;
    }

}
