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
package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;

/** Provides the bazel build system name string. */
public class BazelBuildSystemProvider implements BuildSystemProvider {

    public static final BazelBuildSystemProvider BAZEL = new BazelBuildSystemProvider();

    @Override
    public ImmutableList<String> buildArtifactDirectories(WorkspaceRoot root) {
        var rootDirName = root.directory().getFileName().toString();
        return ImmutableList.of("bazel-bin", "bazel-genfiles", "bazel-out", "bazel-testlogs", "bazel-" + rootDirName);
    }

}
