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
package com.google.idea.blaze.base.sync.workspace;

import java.nio.file.Path;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;

/** Uses the package path locations to resolve a workspace path. */
public final class WorkspacePathResolverImpl implements WorkspacePathResolver {

    private final WorkspaceRoot workspaceRoot;

    public WorkspacePathResolverImpl(WorkspaceRoot workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        var that = (WorkspacePathResolverImpl) o;
        return Objects.equals(workspaceRoot, that.workspaceRoot);
    }

    @Override
    public Path findPackageRoot(String relativePath) {
        return workspaceRoot.directory();
    }

    @Nullable
    @Override
    public WorkspaceRoot findWorkspaceRoot(Path absoluteFile) {
        return workspaceRoot.isInWorkspace(absoluteFile) ? workspaceRoot : null;
    }

    @Nullable
    @Override
    public WorkspacePath getWorkspacePath(Path absoluteFile) {
        return workspaceRoot.workspacePathForSafe(absoluteFile);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(workspaceRoot);
    }

    @Override
    public ImmutableList<Path> resolveToIncludeDirectories(WorkspacePath relativePath) {
        return ImmutableList.of(workspaceRoot.fileForPath(relativePath));
    }
}
