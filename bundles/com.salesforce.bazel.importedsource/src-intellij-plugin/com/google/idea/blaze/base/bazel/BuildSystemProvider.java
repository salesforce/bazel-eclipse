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

/**
 * Extension points specify the build systems supported by this plugin.<br>
 * The order of the extension points establishes a priority (highest priority first), for situations where we don't have
 * an existing project to use for context (e.g. the 'import project' action).
 *
 * <p>
 * Note, the Eclipse plug-in and language server only supports Bazel. Therefore, this interface is a stripped down
 * version of the IJ one with only the things needed.
 * </p>
 */
public interface BuildSystemProvider {

    /** Directories containing artifacts produced during the build process. */
    ImmutableList<String> buildArtifactDirectories(WorkspaceRoot root);

}
