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

import org.junit.jupiter.api.Test;

/** Tests for workspace path validation */
public class WorkspacePathTest {

    @Test
    public void testStringConcatenationConstructor() {
        var empty = new WorkspacePath("");
        var dot = new WorkspacePath(".");
        var foo = new WorkspacePath("foo");
        var dotBar = new WorkspacePath("./bar");

        assertThat(new WorkspacePath(empty, "baz").relativePath()).isEqualTo("baz");
        assertThat(new WorkspacePath(dot, "baz").relativePath()).isEqualTo("baz");
        assertThat(new WorkspacePath(foo, "baz").relativePath()).isEqualTo("foo/baz");
        assertThat(new WorkspacePath(dotBar, "baz").relativePath()).isEqualTo("./bar/baz");
    }

    @Test
    public void testValidation() {
        // Valid workspace paths
        assertThat(WorkspacePath.isValid("")).isTrue();
        assertThat(WorkspacePath.isValid("foo")).isTrue();
        assertThat(WorkspacePath.isValid("foo")).isTrue();
        assertThat(WorkspacePath.isValid("foo/bar")).isTrue();
        assertThat(WorkspacePath.isValid("foo/bar/baz")).isTrue();

        // Invalid workspace paths
        assertThat(WorkspacePath.isValid("/foo")).isFalse();
        assertThat(WorkspacePath.isValid("//foo")).isFalse();
        assertThat(WorkspacePath.isValid("/")).isFalse();
        assertThat(WorkspacePath.isValid("foo/")).isFalse();
        assertThat(WorkspacePath.isValid("foo:")).isFalse();
        assertThat(WorkspacePath.isValid(":")).isFalse();
        assertThat(WorkspacePath.isValid("foo:bar")).isFalse();

        assertThat(WorkspacePath.validate("/foo"))
                .isEqualTo("Workspace path must be relative; cannot start with '/': /foo");

        assertThat(WorkspacePath.validate("/")).isEqualTo("Workspace path must be relative; cannot start with '/': /");

        assertThat(WorkspacePath.validate("foo/")).isEqualTo("Workspace path may not end with '/': foo/");

        assertThat(WorkspacePath.validate("foo:bar")).isEqualTo("Workspace path may not contain ':': foo:bar");
    }
}
