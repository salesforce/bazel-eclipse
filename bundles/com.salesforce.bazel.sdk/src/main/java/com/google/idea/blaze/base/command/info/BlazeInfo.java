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
package com.google.idea.blaze.base.command.info;

import java.nio.file.Path;

import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;

/** The data output by blaze info. */
public interface BlazeInfo {

    String BAZEL_TESTLOGS = "bazel-testlogs";
    String BAZEL_GENFILES = "bazel-genfiles";
    String BAZEL_BIN = "bazel-bin";

    ExecutionRootPath getBlazeBin();

    default Path getBlazeBinDirectory() {
        return getBlazeBin().getPathRootedAt(getExecutionRoot());
    }

    ExecutionRootPath getBlazeGenfiles();

    ExecutionRootPath getBlazeTestlogs();

    default Path getBlazeTestlogsDirectory() {
        return getBlazeTestlogs().getPathRootedAt(getExecutionRoot());
    }

    Path getExecutionRoot();

    default Path getGenfilesDirectory() {
        return getBlazeGenfiles().getPathRootedAt(getExecutionRoot());
    }

    Path getOutputBase();

}
