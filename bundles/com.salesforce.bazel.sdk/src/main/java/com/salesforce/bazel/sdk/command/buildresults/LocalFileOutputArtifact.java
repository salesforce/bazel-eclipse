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
package com.salesforce.bazel.sdk.command.buildresults;

import java.nio.file.Path;
import java.util.Objects;

/** A blaze output artifact which exists on the local file system. */
public class LocalFileOutputArtifact implements OutputArtifact {

    private final Path path;
    private final String blazeOutRelativePath;
    private final String configurationMnemonic;

    public LocalFileOutputArtifact(Path path, String blazeOutRelativePath, String configurationMnemonic) {
        this.path = path;
        this.blazeOutRelativePath = blazeOutRelativePath;
        this.configurationMnemonic = configurationMnemonic;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        var other = (LocalFileOutputArtifact) obj;
        return Objects.equals(path, other.path);
    }

    @Override
    public String getConfigurationMnemonic() {
        return configurationMnemonic;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public String getRelativePath() {
        return blazeOutRelativePath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return blazeOutRelativePath;
    }
}