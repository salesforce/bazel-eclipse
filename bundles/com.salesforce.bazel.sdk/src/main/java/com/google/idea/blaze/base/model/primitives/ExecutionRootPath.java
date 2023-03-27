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

import java.io.File;
import java.nio.file.Path;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.idea.blaze.base.ideinfo.ProjectDataInterner;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;

/**
 * An absolute or relative path returned from Blaze. If it is a relative path, it is relative to the execution root.
 */
public final class ExecutionRootPath implements ProtoWrapper<String> {
    /**
     * Returns the relative {@link ExecutionRootPath} if {@code root} is an ancestor of {@code path} otherwise returns
     * null.
     */
    @Nullable
    public static ExecutionRootPath createAncestorRelativePath(Path root, Path path) {
        // We cannot find the relative path between an absolute and relative path.
        // The underlying code will make the relative path absolute
        // by rooting it at the current working directory which is almost never what you want.
        if (root.isAbsolute() != path.isAbsolute()) {
            return null;
        }
        if (!isAncestor(root, path)) {
            return null;
        }
        var relativePath = root.relativize(path);
        return ProjectDataInterner.intern(new ExecutionRootPath(relativePath));
    }

    public static ExecutionRootPath fromProto(String proto) {
        return ProjectDataInterner.intern(new ExecutionRootPath(proto));
    }

    /**
     * @param possibleParent
     * @param possibleChild
     * @param strict
     *            if {@code false} then this method returns {@code true} if {@code possibleParent} equals to
     *            {@code possibleChild}.
     */
    public static boolean isAncestor(ExecutionRootPath possibleParent, ExecutionRootPath possibleChild,
            boolean strict) {
        return isAncestor(possibleParent.getAbsoluteOrRelativeFile().getPath(),
            possibleChild.getAbsoluteOrRelativeFile().getPath(), strict);
    }

    /**
     * @param possibleParent
     * @param possibleChildPath
     * @param strict
     *            if {@code false} then this method returns {@code true} if {@code possibleParent} equals to
     *            {@code possibleChild}.
     */
    public static boolean isAncestor(ExecutionRootPath possibleParent, String possibleChildPath, boolean strict) {
        return isAncestor(possibleParent.getAbsoluteOrRelativeFile().getPath(), possibleChildPath, strict);
    }

    private static boolean isAncestor(Path possibleParentPath, Path possibleChildPath) {
        return possibleChildPath.startsWith(possibleParentPath);
    }

    /**
     * @param possibleParentPath
     * @param possibleChild
     * @param strict
     *            if {@code false} then this method returns {@code true} if {@code possibleParent} equals to
     *            {@code possibleChild}.
     */
    public static boolean isAncestor(String possibleParentPath, ExecutionRootPath possibleChild, boolean strict) {
        return isAncestor(possibleParentPath, possibleChild.getAbsoluteOrRelativeFile().getPath(), strict);
    }

    /**
     * @param possibleParentPath
     * @param possibleChildPath
     * @param strict
     *            if {@code false} then this method returns {@code true} if {@code possibleParent} equals to
     *            {@code possibleChild}.
     */
    public static boolean isAncestor(String possibleParentPath, String possibleChildPath, boolean strict) {
        return isAncestor(Path.of(possibleChildPath), Path.of(possibleParentPath));
    }

    private final Path path;

    public ExecutionRootPath(Path path) {
        this.path = path;
    }

    public ExecutionRootPath(String path) {
        this.path = Path.of(path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        var that = (ExecutionRootPath) o;
        return Objects.equal(path, that.path);
    }

    public File getAbsoluteOrRelativeFile() {
        return path.toFile();
    }

    public Path getAbsoluteOrRelativePath() {
        return path;
    }

    public File getFileRootedAt(File absoluteRoot) {
        if (path.isAbsolute()) {
            return path.toFile();
        }
        return new File(absoluteRoot, path.toString());
    }

    public Path getPathRootedAt(Path absoluteRoot) {
        if (path.isAbsolute()) {
            return path;
        }
        return absoluteRoot.resolve(absoluteRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }

    public boolean isAbsolute() {
        return path.isAbsolute();
    }

    @Override
    public String toProto() {
        return path.toString();
    }

    @Override
    public String toString() {
        return "ExecutionRootPath{" + "path='" + path + '\'' + '}';
    }
}
