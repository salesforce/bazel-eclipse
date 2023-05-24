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
package com.google.idea.blaze.base.command.buildresult;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.file.Files.newInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.MustBeClosed;

/** A blaze build artifact, either a source or output (generated) artifact. */
public interface BlazeArtifact {

    /** A file artifact available on the local file system. */
    public interface LocalFileArtifact extends BlazeArtifact {
        @Override
        @MustBeClosed
        default BufferedInputStream getInputStream() throws IOException {
            return new BufferedInputStream(newInputStream(getPath()));
        }

        @Override
        default long getLength() {
            try {
                return Files.size(getPath());
            } catch (IOException e) {
                return 0;
            }
        }

        /** {@return the file system path} */
        Path getPath();
    }

    /**
     * Filters out non-local artifacts.
     *
     * <p>
     * Some callers will only ever accept local outputs (e.g. when debugging, and making use of runfiles directories).
     */
    static ImmutableList<Path> getLocalFiles(Collection<? extends BlazeArtifact> artifacts) {
        return artifacts.stream().filter(a -> a instanceof LocalFileArtifact)
                .map(a -> ((LocalFileArtifact) a).getPath()).collect(toImmutableList());
    }

    default void copyTo(Path dest) throws IOException {
        throw new UnsupportedOperationException("The artifact should not be copied.");
    }

    /** A buffered input stream providing the contents of this artifact. */
    @MustBeClosed
    BufferedInputStream getInputStream() throws IOException;

    /** Returns the length of the underlying file in bytes, or 0 if this can't be determined. */
    long getLength();
}
