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
import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.command.BazelQueryForTargetProtoCommand;
import com.salesforce.bazel.sdk.command.querylight.Target;
import com.salesforce.bazel.sdk.model.BazelLabel;

public final class BazelPackageInfo extends BazelElementInfo {

    private static final BazelVisibility DEFAULT_VISIBILITY_PRIVATE = new BazelVisibility(BazelVisibility.PRIVATE);

    private static Logger LOG = LoggerFactory.getLogger(BazelPackageInfo.class);

    /**
     * Finds a {@link IProject} for a given package.
     * <p>
     * This method works without opening/loading the model.
     * </p>
     *
     * @param bazelPackage
     *            the package to find the project for
     * @return the found project (maybe <code>null</code>)
     * @throws CoreException
     */
    static IProject findProject(BazelPackage bazelPackage) throws CoreException {
        var workspaceRoot = bazelPackage.getBazelWorkspace().getLocation();
        // we don't care about the actual project name - we look for the property
        var projects = getEclipseWorkspaceRoot().getProjects();
        for (IProject project : projects) {
            if (project.isAccessible() // is open
                    && project.hasNature(BAZEL_NATURE_ID) // is a Bazel project
                    && hasWorkspaceRootPropertySetToLocation(project, workspaceRoot) // belongs to the workspace root
                    && hasOwnerPropertySetForLabel(project, bazelPackage.getLabel()) // represents the target
            ) {
                return project;
            }
        }

        return null;
    }

    static Map<String, Target> queryForTargets(BazelPackage bazelPackage,
            BazelElementCommandExecutor bazelElementCommandExecutor) throws CoreException {

        var result =
                queryForTargets(bazelPackage.getBazelWorkspace(), List.of(bazelPackage), bazelElementCommandExecutor)
                        .get(bazelPackage);
        return result != null ? result : Collections.emptyMap();
    }

    static Map<BazelPackage, Map<String, Target>> queryForTargets(BazelWorkspace bazelWorkspace,
            Collection<BazelPackage> bazelPackages, BazelElementCommandExecutor bazelElementCommandExecutor)
            throws CoreException {
        // bazel query '"//foo:all" + "//bar:all"'

        if (bazelPackages.isEmpty()) {
            return Collections.emptyMap();
        }

        var workspaceRoot = bazelWorkspace.getLocation().toPath();
        var query = bazelPackages.stream()
                .map(bazelPackage -> format("//%s:all", bazelPackage.getWorkspaceRelativePath()))
                .collect(joining(" + "));

        Map<String, BazelPackage> bazelPackageByWorkspaceRelativePath = new HashMap<>();
        bazelPackages.stream()
                .forEach(p -> bazelPackageByWorkspaceRelativePath.put(p.getWorkspaceRelativePath().toString(), p));

        Map<BazelPackage, Map<String, Target>> result = new HashMap<>();
        LOG.debug("{}: querying Bazel for list of targets from: {}", bazelWorkspace, query);
        var queryResult = bazelElementCommandExecutor.runQueryWithoutLock(
            new BazelQueryForTargetProtoCommand(
                    workspaceRoot,
                    query,
                    true /* keep going */,
                    List.of("--noproto:locations", "--noproto:default_values"),
                    format(
                        "Loading targets for %d %s",
                        bazelPackages.size(),
                        bazelPackages.size() == 1 ? "package" : "packages")));
        for (Target target : queryResult) {
            if (!target.hasRule()) {
                LOG.trace("{}: ignoring target: {}", bazelWorkspace, target);
                continue;
            }

            LOG.trace("{}: found target: {}", bazelWorkspace, target);
            var targetLabel = new BazelLabel(target.rule().name());

            var bazelPackage = bazelPackageByWorkspaceRelativePath.get(targetLabel.getPackagePath());
            if (bazelPackage == null) {
                LOG.debug("{}: ignoring target for unknown package: {}", bazelWorkspace, targetLabel);
                continue;
            }
            if (!result.containsKey(bazelPackage)) {
                result.put(bazelPackage, new HashMap<>());
            }

            var targetName = targetLabel.getTargetName();
            result.get(bazelPackage).put(targetName, target);
        }
        return result;
    }

    private final Path buildFile;
    private final BazelPackage bazelPackage;
    private final Map<String, Target> indexOfTargetInfoByTargetName;

    private volatile BazelProject bazelProject;

    private BazelVisibility defaultVisibility;

    BazelPackageInfo(Path buildFile, BazelPackage bazelPackage,
            Map<String, Target> indexOfTargetInfoByTargetName) {
        this.buildFile = buildFile;
        this.bazelPackage = bazelPackage;
        this.indexOfTargetInfoByTargetName = indexOfTargetInfoByTargetName;
    }

    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    public BazelProject getBazelProject() throws CoreException {
        var cachedProject = bazelProject;
        if (cachedProject != null) {
            return cachedProject;
        }

        var project = findProject(getBazelPackage());
        if (project == null) {
            throw new CoreException(
                    Status.error(
                        format(
                            "Unable to find project for Bazel package '%s' in the Eclipse workspace. Please check the workspace setup!",
                            getBazelPackage().getLabel())));
        }
        return bazelProject = new BazelProject(project, getBazelPackage().getModel());
    }

    public Path getBuildFile() {
        return buildFile;
    }

    public BazelVisibility getDefaultVisibility() throws CoreException {
        var cachedVisibility = defaultVisibility;
        if (cachedVisibility != null) {
            return cachedVisibility;
        }
        var bazelBuildFile = getBazelPackage().getBazelBuildFile();
        var packageCall = bazelBuildFile.getInfo().getPackageCall();
        if (packageCall == null) {
            return defaultVisibility = DEFAULT_VISIBILITY_PRIVATE;
        }
        var defaultVisibilityValue = packageCall.getStringListArgument("default_visibility");
        if ((defaultVisibilityValue == null) || defaultVisibilityValue.isEmpty()) {
            return defaultVisibility = DEFAULT_VISIBILITY_PRIVATE;
        }

        return defaultVisibility = new BazelVisibility(defaultVisibilityValue);
    }

    @Override
    public BazelPackage getOwner() {
        return bazelPackage;
    }

    Target getTarget(String targetName) {
        return indexOfTargetInfoByTargetName.get(targetName);
    }

    public Set<String> getTargets() {
        return Collections.unmodifiableSet(indexOfTargetInfoByTargetName.keySet());
    }

}
