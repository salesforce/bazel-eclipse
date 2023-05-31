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
package com.google.idea.blaze.java.sync.model;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetKey;

/** An immutable reference to a .jar required by a rule. */
@Immutable
public final class BlazeJarLibrary {

    public final LibraryArtifact libraryArtifact;

    @Nullable
    public final TargetKey targetKey;

    public BlazeJarLibrary(LibraryArtifact libraryArtifact, @Nullable TargetKey targetKey) {
        this.libraryArtifact = libraryArtifact;
        this.targetKey = targetKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlazeJarLibrary that)) {
            return false;
        }

        return super.equals(other) && Objects.equals(libraryArtifact, that.libraryArtifact);
    }

    public String getExtension() {
        return ".jar";
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), libraryArtifact);
    }

    @Override
    public String toString() {
        return "BlazeJarLibrary [" + targetKey + ", " + libraryArtifact + "]";
    }
}
