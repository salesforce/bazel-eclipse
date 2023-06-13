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

import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readAllLines;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;

/** A parser for .bazelgnore files, which tells Bazel a list of paths to ignore. */
public class BazelIgnoreParser {

    private static Logger LOG = LoggerFactory.getLogger(BazelIgnoreParser.class);

    private final Path bazelIgnoreFile;

    public BazelIgnoreParser(WorkspaceRoot workspaceRoot) {
        this.bazelIgnoreFile = workspaceRoot.fileForPath(new WorkspacePath(".bazelignore"));
    }

    /**
     * Parse a .bazelignore file (if it exists) for workspace relative paths.
     *
     * @return a list of validated WorkspacePaths.
     */
    public List<WorkspacePath> getIgnoredPaths() {
        if (!isRegularFile(bazelIgnoreFile)) {
            return List.of();
        }

        ImmutableList.Builder<WorkspacePath> ignoredPaths = ImmutableList.builder();

        try {
            for (String path : readAllLines(bazelIgnoreFile)) {
                if (path.trim().isEmpty() || path.trim().startsWith("#")) {
                    continue;
                }

                if (path.endsWith("/")) {
                    // .bazelignore allows the "/" path suffix, but WorkspacePath doesn't.
                    path = path.substring(0, path.length() - 1);
                }

                if (!WorkspacePath.isValid(path)) {
                    LOG.warn(String.format("Found %s in .bazelignore, but unable to parse as relative workspace path.",
                        path));
                    continue;
                }

                ignoredPaths.add(new WorkspacePath(path));
            }
        } catch (IOException e) {
            LOG.warn(String.format("Unable to read .bazelignore file even though it exists."));
        }

        return ignoredPaths.build();
    }
}
