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

import static org.eclipse.core.runtime.IPath.fromPath;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.protobuf.Timestamp;

/** Parses output artifacts from the blaze build event protocol (BEP). */
public interface OutputOrSourceArtifactParser {

    /** The default implementation of {@link OutputOrSourceArtifactParser}, for local, absolute file paths. */
    class LocalFileParser implements OutputOrSourceArtifactParser {
    	private static Logger LOG = LoggerFactory.getLogger(OutputOrSourceArtifactParser.LocalFileParser.class);
        private static String getBlazeOutRelativePath(BuildEventStreamProtos.File file, Path pathFromUri, BlazeInfo blazeInfo) {
            List<String> pathPrefixList = file.getPathPrefixList();
            if (pathPrefixList.size() <= 1) {
            	// this might be a source artifact, i.e. not in bazel-out at all
            	if(pathFromUri.startsWith(blazeInfo.getOutputBase())) {
            		// as we don't know anything about bazel-out here, we attempt to relativize to bazel-bin
            		Path bazelOut = blazeInfo.getBlazeBinDirectory().resolve("../../").normalize();
					return fromPath(bazelOut.relativize(pathFromUri)).toString(); // Bazel uses posix style paths)
            	}
            	// not in bazel-out
                return null;
            }

            // remove the initial 'bazel-out' path component
            var prefix = Joiner.on('/').join(pathPrefixList.subList(1, pathPrefixList.size()));
            return prefix + "/" + file.getName();
        }

        @Override
        @Nullable
        public BlazeArtifact parse(BuildEventStreamProtos.File file, String configurationMnemonic,
                Timestamp startTime, BlazeInfo blazeInfo) {
            var uri = file.getUri();
            if (!uri.startsWith("file:")) {
                return null;
            }
            try {
                var path = new File(new URI(uri)).toPath();
                String blazeOutRelativePath = getBlazeOutRelativePath(file, path, blazeInfo);
                if(blazeOutRelativePath == null) {
                	// this is a source artifact (java_import?)
    				return new SourceArtifact(path);
                }
				return new LocalFileOutputArtifact(path, blazeOutRelativePath, configurationMnemonic);
            } catch (URISyntaxException | IllegalArgumentException e) {
            	LOG.warn("Exception parsing artifact '{}' from BEP: {}", uri, e.getMessage(), e);
                return null;
            }
        }
    }

    @Nullable
    static BlazeArtifact parseArtifact(BuildEventStreamProtos.File file, String configurationMnemonic,
            Timestamp startTime, BlazeInfo blazeInfo) {
        return new LocalFileParser().parse(file, configurationMnemonic, startTime, blazeInfo);
    }

    @Nullable
    BlazeArtifact parse(BuildEventStreamProtos.File file, String configurationMnemonic, Timestamp startTime, BlazeInfo blazeInfo);
}