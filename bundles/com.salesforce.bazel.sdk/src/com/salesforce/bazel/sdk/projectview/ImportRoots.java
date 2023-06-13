/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.salesforce.bazel.sdk.projectview;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.util.WorkspacePathUtil;

/** The roots to import. Derived from project view. */
public final class ImportRoots {

    /** Builder for import roots */
    public static class Builder {
        private static boolean hasWorkspaceRoot(ImmutableCollection<WorkspacePath> rootDirectories) {
            return rootDirectories.stream().anyMatch(WorkspacePath::isWorkspaceRoot);
        }

        private final ImmutableCollection.Builder<WorkspacePath> rootDirectoriesBuilder = ImmutableList.builder();
        private final ImmutableSet.Builder<WorkspacePath> excludeDirectoriesBuilder = ImmutableSet.builder();
        private final ImmutableList.Builder<TargetExpression> projectTargets = ImmutableList.builder();

        private boolean deriveTargetsFromDirectories = false;
        private final WorkspaceRoot workspaceRoot;

        private final BuildSystemProvider buildSystemProvider;

        private Builder(WorkspaceRoot workspaceRoot, BuildSystemProvider buildSystemProvider) {
            this.workspaceRoot = workspaceRoot;
            this.buildSystemProvider = buildSystemProvider;
        }

        public Builder addExcludeDirectory(WorkspacePath directory) {
            excludeDirectoriesBuilder.add(directory);
            return this;
        }

        public Builder addRootDirectory(WorkspacePath directory) {
            rootDirectoriesBuilder.add(directory);
            return this;
        }

        public Builder addTarget(TargetExpression target) {
            projectTargets.add(target);
            return this;
        }

        public ImportRoots build() {
            var rootDirectories = rootDirectoriesBuilder.build();
            if (hasWorkspaceRoot(rootDirectories)) {
                excludeBuildSystemArtifacts();
            }
            excludeBazelIgnoredPaths();

            var minimalExcludes = WorkspacePathUtil.calculateMinimalWorkspacePaths(excludeDirectoriesBuilder.build());

            // Remove any duplicates, overlapping, or excluded directories
            var minimalRootDirectories =
                    WorkspacePathUtil.calculateMinimalWorkspacePaths(rootDirectories, minimalExcludes);

            var directories = new ProjectDirectoriesHelper(minimalRootDirectories, minimalExcludes);

            var targets = deriveTargetsFromDirectories
                    ? TargetExpressionList.createWithTargetsDerivedFromDirectories(projectTargets.build(), directories)
                    : TargetExpressionList.create(projectTargets.build());

            return new ImportRoots(directories, targets);
        }

        private void excludeBazelIgnoredPaths() {
            excludeDirectoriesBuilder.addAll(new BazelIgnoreParser(workspaceRoot).getIgnoredPaths());
        }

        private void excludeBuildSystemArtifacts() {
            for (String dir : buildSystemProvider.buildArtifactDirectories(workspaceRoot)) {
                excludeDirectoriesBuilder.add(new WorkspacePath(dir));
            }
        }

        public Builder setDeriveTargetsFromDirectories(boolean deriveTargetsFromDirectories) {
            this.deriveTargetsFromDirectories = deriveTargetsFromDirectories;
            return this;
        }
    }

    public static Builder builder(WorkspaceRoot workspaceRoot) {
        return new Builder(workspaceRoot, BazelBuildSystemProvider.BAZEL);
    }

    private final ProjectDirectoriesHelper projectDirectories;
    private final TargetExpressionList projectTargets;

    private ImportRoots(ProjectDirectoriesHelper projectDirectories, TargetExpressionList projectTargets) {
        this.projectDirectories = projectDirectories;
        this.projectTargets = projectTargets;
    }

    public boolean containsWorkspacePath(WorkspacePath workspacePath) {
        return projectDirectories.containsWorkspacePath(workspacePath);
    }

    public Set<WorkspacePath> excludeDirectories() {
        return projectDirectories.excludeDirectories;
    }

    public ImmutableSet<Path> excludePaths() {
        return projectDirectories.excludeDirectories.stream().map(WorkspacePath::asPath).collect(toImmutableSet());
    }

    /** Returns true if this rule should be imported as source. */
    public boolean importAsSource(Label label) {
        if (label.isExternal()) {
            return false;
        }
        return projectDirectories.containsWorkspacePath(label.blazePackage()) || targetInProject(label);
    }

    public boolean packageInProjectTargets(WorkspacePath packagePath) {
        return projectTargets.includesPackage(packagePath);
    }

    public Collection<WorkspacePath> rootDirectories() {
        return projectDirectories.rootDirectories;
    }

    /** Returns the import roots, as paths relative to the workspace root. */
    public ImmutableSet<Path> rootPaths() {
        return projectDirectories.rootDirectories.stream().map(WorkspacePath::asPath).collect(toImmutableSet());
    }

    /**
     * Returns true if this target is covered by the project view. Assumes wildcard target patterns (explicit or derived
     * from directories) cover all targets in the relevant packages.
     */
    public boolean targetInProject(Label label) {
        return projectTargets.includesTarget(label);
    }
}
