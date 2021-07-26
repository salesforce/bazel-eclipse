/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.sdk.path;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Pluggable strategy for splitting a source path into two components like:
 * <ul>
 * <li>src/main/java</li>
 * <li>com/salesforce/foo/Foo.java</li>
 * </ul>
 */
public abstract class SourcePathSplitterStrategy {

    // STATIC

    /**
     * Map of pluggable strategies used to split a source path (src/main/java/com/salesforce/foo/Foo.java) into two
     * components based on language specific rules:
     * <ul>
     * <li>src/main/java</li>
     * <li>com/salesforce/foo/Foo.java</li>
     * </ul>
     * <p>
     * This Map is public because each supported language will need to plug one or more strategies into the map. The key
     * is a String file extension (.java) and the value is the splitter for that file type.
     */
    public static Map<String, SourcePathSplitterStrategy> splitterStrategies = new HashMap<>();

    /**
     * Looks up the splitter using the extension from the sourceFilePath (a/b/c/d/Foo.java)
     */
    public static SourcePathSplitterStrategy getSplitterForFilePath(String sourceFilePath) {
        SourcePathSplitterStrategy splitter = null;
        int lastDot = sourceFilePath.lastIndexOf(".");
        if (lastDot > -1) {
            String extension = sourceFilePath.substring(lastDot);
            splitter = SourcePathSplitterStrategy.splitterStrategies.get(extension);
        }
        return splitter;
    }

    // INSTANCES

    /**
     * Split a source path (src/main/java/com/salesforce/foo/Foo.java) into two components based on language specific
     * rules:
     * <ul>
     * <li>src/main/java</li>
     * <li>com/salesforce/foo/Foo.java</li>
     * </ul>
     */
    public abstract SplitSourcePath splitSourcePath(File basePath, String relativePathToSourceFile);

}
