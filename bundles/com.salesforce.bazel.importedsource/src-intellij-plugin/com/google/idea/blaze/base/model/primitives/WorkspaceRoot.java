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
package com.google.idea.blaze.base.model.primitives;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;

import javax.annotation.Nullable;

import com.google.idea.blaze.base.ideinfo.ProtoWrapper;

/** Represents a workspace root */
public class WorkspaceRoot implements ProtoWrapper<String> {

    public static WorkspaceRoot fromProto(String proto) {
        return new WorkspaceRoot(Path.of(proto));
    }

    private final Path directory;

    public WorkspaceRoot(Path directory) {
        this.directory = directory;
    }

    public Path absolutePathFor(String workspaceRelativePath) {
        return path().resolve(workspaceRelativePath);
    }

    public Path directory() {
        return directory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }

        var that = (WorkspaceRoot) o;
        return directory.equals(that.directory);
    }

    public Path fileForPath(WorkspacePath workspacePath) {
        return directory.resolve(workspacePath.relativePath());
    }

    @Override
    public int hashCode() {
        return directory.hashCode();
    }

    public boolean isInWorkspace(Path path) {
        return requireNonNull(path).startsWith(directory);
    }

    public Path path() {
        return directory;
    }

    @Override
    public String toProto() {
        return directory.toString();
    }

    @Override
    public String toString() {
        return directory.toString();
    }

    public WorkspacePath workspacePathFor(Path path) {
        if (!isInWorkspace(path)) {
            throw new IllegalArgumentException(String.format("File '%s' is not under workspace %s", path, directory));
        }
        if (directory.equals(path)) {
            return new WorkspacePath("");
        }
        return new WorkspacePath(directory.relativize(path).toString());
    }

    /**
     * Returns the WorkspacePath for the given absolute file, if it's a child of this WorkspaceRoot and a valid
     * WorkspacePath. Otherwise returns null.
     */
    @Nullable
    public WorkspacePath workspacePathForSafe(Path absoluteFile) {
        if (!isInWorkspace(absoluteFile)) {
            return null;
        }
        if (directory.equals(absoluteFile)) {
            return new WorkspacePath("");
        }
        return WorkspacePath.createIfValid(directory.relativize(absoluteFile).toString());
    }

}
