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

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

import com.google.common.io.CountingInputStream;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;

/** Provides {@link BuildEventStreamProtos.BuildEvent} */
public interface BuildEventStreamProvider {

    /** An exception parsing a stream of build events. */
    class BuildEventStreamException extends IOException {
        private static final long serialVersionUID = 1L;

        public BuildEventStreamException(String message) {
            super(message);
        }

        public BuildEventStreamException(String message, Throwable e) {
            super(message, e);
        }
    }

    static BuildEventStreamProvider fromInputStream(InputStream stream) {
        var countingStream = new CountingInputStream(stream);
        return new BuildEventStreamProvider() {
            @Override
            public long getBytesConsumed() {
                return countingStream.getCount();
            }

            @Nullable
            @Override
            public BuildEvent getNext() throws BuildEventStreamException {
                return parseNextEventFromStream(countingStream);
            }

            @Nullable
            BuildEventStreamProtos.BuildEvent parseNextEventFromStream(InputStream stream)
                    throws BuildEventStreamException {
                try {
                    return BuildEventStreamProtos.BuildEvent.parseDelimitedFrom(stream);
                } catch (IOException e) {
                    throw new BuildEventStreamException(e.getMessage(), e);
                }
            }
        };
    }

    long getBytesConsumed();

    /** Returns the next build event in the stream, or null if there are none remaining. */
    @Nullable
    BuildEventStreamProtos.BuildEvent getNext() throws BuildEventStreamException;
}