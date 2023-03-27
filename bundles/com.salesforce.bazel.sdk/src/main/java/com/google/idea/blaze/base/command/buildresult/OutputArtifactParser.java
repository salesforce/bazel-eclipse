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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.protobuf.Timestamp;

/** Parses output artifacts from the blaze build event protocol (BEP). */
public interface OutputArtifactParser {

    /** The default implementation of {@link OutputArtifactParser}, for local, absolute file paths. */
    class LocalFileParser implements OutputArtifactParser {
        private static String getBlazeOutRelativePath(BuildEventStreamProtos.File file) {
            List<String> pathPrefixList = file.getPathPrefixList();
            if (pathPrefixList.size() <= 1) {
                throw new IllegalArgumentException(
                        "Invalid output from BuildEventStream. No longer support: " + pathPrefixList);
            }

            // remove the initial 'bazel-out' path component
            var prefix = Joiner.on('/').join(pathPrefixList.subList(1, pathPrefixList.size()));
            return prefix + "/" + file.getName();
        }

        @Override
        @Nullable
        public OutputArtifact parse(BuildEventStreamProtos.File file, String configurationMnemonic,
                Timestamp startTime) {
            var uri = file.getUri();
            if (!uri.startsWith("file:")) {
                return null;
            }
            try {
                var path = new File(new URI(uri)).toPath();
                return new LocalFileOutputArtifact(path, getBlazeOutRelativePath(file), configurationMnemonic);
            } catch (URISyntaxException | IllegalArgumentException e) {
                return null;
            }
        }
    }

    @Nullable
    static OutputArtifact parseArtifact(BuildEventStreamProtos.File file, String configurationMnemonic,
            Timestamp startTime) {
        return new LocalFileParser().parse(file, configurationMnemonic, startTime);
    }

    @Nullable
    OutputArtifact parse(BuildEventStreamProtos.File file, String configurationMnemonic, Timestamp startTime);
}