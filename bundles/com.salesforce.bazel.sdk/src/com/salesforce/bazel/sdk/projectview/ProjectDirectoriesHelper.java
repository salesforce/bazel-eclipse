/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import java.nio.file.FileSystems;
import java.util.Collection;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;

class ProjectDirectoriesHelper {
    private static boolean isSubdirectory(WorkspacePath ancestor, WorkspacePath descendant) {
        if (ancestor.isWorkspaceRoot()) {
            return true;
        }
        var ancestorPath = FileSystems.getDefault().getPath(ancestor.relativePath());
        var descendantPath = FileSystems.getDefault().getPath(descendant.relativePath());
        return descendantPath.startsWith(ancestorPath);
    }

    final ImmutableSet<WorkspacePath> rootDirectories;

    final ImmutableSet<WorkspacePath> excludeDirectories;

    @VisibleForTesting
    ProjectDirectoriesHelper(Collection<WorkspacePath> rootDirectories, Collection<WorkspacePath> excludeDirectories) {
        this.rootDirectories = ImmutableSet.copyOf(rootDirectories);
        this.excludeDirectories = ImmutableSet.copyOf(excludeDirectories);
    }

    boolean containsWorkspacePath(WorkspacePath workspacePath) {
        var included = false;
        var excluded = false;
        for (WorkspacePath rootDirectory : rootDirectories) {
            included = included || isSubdirectory(rootDirectory, workspacePath);
        }
        for (WorkspacePath excludeDirectory : excludeDirectories) {
            excluded = excluded || isSubdirectory(excludeDirectory, workspacePath);
        }
        return included && !excluded;
    }

    public boolean isExcluded(WorkspacePath workspacePath) {
        for (WorkspacePath excludeDirectory : excludeDirectories) {
            if (isSubdirectory(excludeDirectory, workspacePath)) {
                return true;
            }
        }
        return false;
    }
}