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
package com.google.idea.blaze.base.sync.workspace;

import java.nio.file.Path;
import java.util.Objects;

import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.SourceArtifact;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;

/** Decodes intellij_ide_info.proto ArtifactLocation file paths */
public final class ArtifactLocationDecoderImpl implements ArtifactLocationDecoder {
    private final BlazeInfo blazeInfo;
    private final WorkspacePathResolver pathResolver;

    public ArtifactLocationDecoderImpl(BlazeInfo blazeInfo, WorkspacePathResolver pathResolver) {
        this.blazeInfo = blazeInfo;
        this.pathResolver = pathResolver;
    }

    public Path decode(ArtifactLocation artifactLocation) {
        if (artifactLocation.isMainWorkspaceSourceArtifact()) {
            return pathResolver.resolveToFile(artifactLocation.getRelativePath());
        }

        // resolve from execution root
        // doesn't require file-system operations -- no attempt to resolve symlinks.
        return blazeInfo.getExecutionRoot().resolve(artifactLocation.getExecutionRootRelativePath());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        var that = (ArtifactLocationDecoderImpl) o;
        return Objects.equals(blazeInfo, that.blazeInfo) && Objects.equals(pathResolver, that.pathResolver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blazeInfo, pathResolver);
    }

    /**
     * Derives a {@link LocalFileOutputArtifact} from a generated {@link ArtifactLocation} under blaze-out.
     *
     * <p>
     * If the exec-root path is of an unexpected form, falls back to returning a {@link SourceArtifact}.
     */
    private BlazeArtifact outputArtifactFromExecRoot(ArtifactLocation location) {
        // exec-root-relative path of the form 'blaze-out/mnemonic/genfiles/path'
        var execRootPath = location.getExecutionRootRelativePath();
        var ix1 = execRootPath.indexOf('/');
        var ix2 = execRootPath.indexOf('/', ix1 + 1);
        if (ix2 == -1) {
            return new SourceArtifact(decode(location));
        }
        var blazeOutPath = execRootPath.substring(ix1 + 1);
        var configMnemonic = execRootPath.substring(ix1 + 1, ix2);
        return new LocalFileOutputArtifact(decode(location), blazeOutPath, configMnemonic);
    }

    @Override
    public BlazeArtifact resolveOutput(ArtifactLocation artifact) {
        if (artifact.isMainWorkspaceSourceArtifact()) {
            return new SourceArtifact(decode(artifact));
        }
        //        BlazeArtifact remoteOutput = remoteOutputs.findRemoteOutput(artifact);
        //        if (remoteOutput != null) {
        //            return remoteOutput;
        //        }
        return outputArtifactFromExecRoot(artifact);
    }

    @Override
    public Path resolveSource(ArtifactLocation artifact) {
        return artifact.isMainWorkspaceSourceArtifact() ? pathResolver.resolveToFile(artifact.getRelativePath()) : null;
    }
}
