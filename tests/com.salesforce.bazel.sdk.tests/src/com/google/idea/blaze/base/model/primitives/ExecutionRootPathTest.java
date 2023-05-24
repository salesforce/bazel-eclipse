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

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Tests execution root path. */
public class ExecutionRootPathTest {
    private static Path asPath(String path) {
        return Path.of(path);
    }

    @Test
    public void testAbsoluteFileDoesNotGetRerooted() {
        var executionRootPath = new ExecutionRootPath("/root/foo/bar");
        var rootedFile = executionRootPath.getFileRootedAt(new File("/core/dev"));
        assertThat(rootedFile).isEqualTo(new File("/root/foo/bar"));
    }

    @Test
    public void testCreateRelativePathWithOneAbsolutePathAndOneRelativePathReturnsNull1() {
        var relativePathFragment = ExecutionRootPath.createAncestorRelativePath(asPath("/code/lib/fastmath"),
            asPath("code/lib/fastmath/lib1"));
        assertThat(relativePathFragment).isNull();
    }

    @Test
    public void testCreateRelativePathWithOneAbsolutePathAndOneRelativePathReturnsNull2() {
        var relativePathFragment =
                ExecutionRootPath.createAncestorRelativePath(asPath("code/lib/fastmath"), asPath("/code/lib/slowmath"));
        assertThat(relativePathFragment).isNull();
    }

    @Test
    public void testCreateRelativePathWithTwoAbsolutePaths() {
        var relativePathFragment = ExecutionRootPath.createAncestorRelativePath(asPath("/code/lib/fastmath"),
            asPath("/code/lib/fastmath/lib1"));
        assertThat(relativePathFragment).isNotNull();
        assertThat(relativePathFragment.getAbsoluteOrRelativeFile()).isEqualTo(new File("lib1"));
    }

    @Test
    public void testCreateRelativePathWithTwoAbsolutePathsWithNoRelativePath() {
        var relativePathFragment =
                ExecutionRootPath.createAncestorRelativePath(asPath("/obj/lib/fastmath"), asPath("/code/lib/slowmath"));
        assertThat(relativePathFragment).isNull();
    }

    @Test
    public void testCreateRelativePathWithTwoRelativePaths() {
        var relativePathFragment = ExecutionRootPath.createAncestorRelativePath(asPath("code/lib/fastmath"),
            asPath("code/lib/fastmath/lib1"));
        assertThat(relativePathFragment).isNotNull();
        assertThat(relativePathFragment.getAbsoluteOrRelativeFile()).isEqualTo(new File("lib1"));
    }

    @Test
    public void testCreateRelativePathWithTwoRelativePathsWithNoRelativePath() {
        var relativePathFragment =
                ExecutionRootPath.createAncestorRelativePath(asPath("obj/lib/fastmath"), asPath("code/lib/slowmath"));
        assertThat(relativePathFragment).isNull();
    }

    @Test
    public void testMultiLevelPathEndInSlash() {
        var executionRootPath = new ExecutionRootPath("foo/bar");
        assertThat(executionRootPath.getAbsoluteOrRelativeFile()).isEqualTo(new File("foo/bar/"));

        var executionRootPath2 = new ExecutionRootPath("foo/bar/");
        assertThat(executionRootPath2.getAbsoluteOrRelativeFile()).isEqualTo(new File("foo/bar/"));
    }

    @Test
    public void testRelativeFileGetsRerooted() {
        var executionRootPath = new ExecutionRootPath("foo/bar");
        var rootedFile = executionRootPath.getFileRootedAt(new File("/root"));
        assertThat(rootedFile).isEqualTo(new File("/root/foo/bar"));
    }

    @Test
    public void testSingleLevelPathEndInSlash() {
        var executionRootPath = new ExecutionRootPath("foo");
        assertThat(executionRootPath.getAbsoluteOrRelativeFile()).isEqualTo(new File("foo/"));

        var executionRootPath2 = new ExecutionRootPath("foo/");
        assertThat(executionRootPath2.getAbsoluteOrRelativeFile()).isEqualTo(new File("foo/"));
    }
}
