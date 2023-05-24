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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.salesforce.bazel.eclipse.core.model.execution.BazelModelCommandExecutionService;
import com.salesforce.bazel.sdk.command.BazelQueryForTargetProtoCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

public final class BazelPackageInfo extends BazelElementInfo {

    private static Logger LOG = LoggerFactory.getLogger(BazelPackageInfo.class);

    private final Path buildFile;
    private final Path workspaceRoot;
    private final BazelPackage bazelPackage;

    private Map<String, Target> indexOfTargetInfoByTargetName;
    private volatile BazelProject bazelProject;

    public BazelPackageInfo(Path buildFile, Path workspaceRoot, BazelPackage bazelPackage) {
        this.buildFile = buildFile;
        this.workspaceRoot = workspaceRoot;
        this.bazelPackage = bazelPackage;
    }

    IProject findProject() throws CoreException {
        var workspaceProject = getBazelPackage().getBazelWorkspace().getBazelProject().getProject();
        var workspaceRoot = getBazelPackage().getBazelWorkspace().getLocation();
        // we don't care about the actual project name - we look for the property
        var projects = getEclipseWorkspaceRoot().getProjects();
        for (IProject project : projects) {
            if (project.hasNature(BAZEL_NATURE_ID) // is a Bazel project
                    && !workspaceProject.equals(project) // is not the workspace project
                    && hasWorkspaceRootPropertySetToLocation(project, workspaceRoot) // belongs to the workspace root
                    && hasOwnerPropertySetForLabel(project, getBazelPackage().getLabel()) // represents the target
            ) {
                return project;
            }
        }

        return null;
    }

    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    public BazelProject getBazelProject() throws CoreException {
        var cachedProject = bazelProject;
        if (cachedProject != null) {
            return cachedProject;
        }

        var project = findProject();
        if (project == null) {
            throw new CoreException(Status.error(format(
                "Unable to find project for Bazel package '%s' in the Eclipse workspace. Please check the workspace setup!",
                getBazelPackage().getLabel())));
        }
        return bazelProject = new BazelProject(project, getBazelPackage().getModel());
    }

    public Path getBuildFile() {
        return buildFile;
    }

    Target getTarget(String targetName) {
        return indexOfTargetInfoByTargetName.get(targetName);
    }

    public Set<String> getTargets() {
        return Collections.unmodifiableSet(indexOfTargetInfoByTargetName.keySet());
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void load(BazelModelCommandExecutionService executionService) throws CoreException {
        // bazel query '"//foo:all"'

        try {
            LOG.debug("{}: querying Bazel for list targets", getBazelPackage());
            var queryResult = executionService.executeOutsideWorkspaceLockAsync(
                new BazelQueryForTargetProtoCommand(getWorkspaceRoot(),
                        format("\"//%s:all\"", getBazelPackage().getWorkspaceRelativePath()), true /* keep going */),
                getBazelPackage()).get();
            Map<String, Target> indexOfTargetInfoByTargetName = new HashMap<>();
            for (Target target : queryResult) {
                if (target.hasRule()) {
                    LOG.trace("{}: found target: {}", getBazelPackage(), target);
                    var targetName = new BazelLabel(target.getRule().getName()).getTargetName();
                    indexOfTargetInfoByTargetName.put(targetName, target);
                } else {
                    LOG.trace("{}: ignoring target: {}", getBazelPackage(), target);
                }
            }
            this.indexOfTargetInfoByTargetName = indexOfTargetInfoByTargetName;
        } catch (InterruptedException e) {
            throw new OperationCanceledException("cancelled");
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause == null) {
                throw new CoreException(Status.error(
                    format("bazel query failed in workspace '%s' for with unknown reason", workspaceRoot), e));
            }
            throw new CoreException(Status.error(
                format("bazel query failed in workspace '%s': %s", workspaceRoot, cause.getMessage()), cause));
        }
    }

}
