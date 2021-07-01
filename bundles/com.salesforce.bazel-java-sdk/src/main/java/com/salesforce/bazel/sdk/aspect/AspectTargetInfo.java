/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
 *
 */

package com.salesforce.bazel.sdk.aspect;

import java.io.File;
import java.util.List;

import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * A parsed version of the JSON file produced by the application of the Bazel aspect. Each target in each package will
 * have such a file. Look for subclasses of this class for extended information added for specific rule types.
 * <p>
 * For example, the JSON document format is like this for a java_library rule:
 *
 * <pre>
 {
   "build_file_artifact_location":"helloworld/BUILD",
   "dependencies":["//proto:helloworld_java_proto"],
   "generated_jars":[],
   "jars":[
     {"interface_jar":"bazel-out/darwin-fastbuild/bin/helloworld/libhelloworld-hjar.jar",
      "jar":"bazel-out/darwin-fastbuild/bin/helloworld/libhelloworld.jar",
      "source_jar":"bazel-out/darwin-fastbuild/bin/helloworld/libhelloworld-src.jar"
     }
    ],
    "kind":"java_library",
    "label":"//helloworld:helloworld",
    "sources":["helloworld/src/main/java/helloworld/HelloWorld.java"]
 }
 * </pre>
 * <p>
 * See resources/bzljavasdk_aspect.bzl for the code that creates the JSON files
 */
public class AspectTargetInfo {

    protected final File aspectDataFile; // full path to the file on the file system
    protected final String workspaceRelativePath; // relative path on the filesystem within the workspace
    protected final List<String> deps;
    protected final String kind;
    protected final String label;
    protected final List<String> sources;

    @Override
    public String toString() {
        StringBuffer builder = new StringBuffer();
        builder.append("AspectTargetInfo(\n");
        builder.append("  label = ").append(label).append(",\n");
        builder.append("  build_file_artifact_location = ").append(workspaceRelativePath).append(",\n");
        builder.append("  kind = ").append(kind).append(",\n");
        builder.append("  sources = [").append(commaJoiner(sources)).append("])\n");
        return builder.toString();
    }

    protected String commaJoiner(List<?> things) {
        StringBuffer sb = new StringBuffer();
        for (Object thing : things) {
            sb.append(thing.toString());
            sb.append(",");
        }
        return sb.toString();
    }

    /**
     * Location of the generated file that was used to build this instance.
     *
     * @return the File
     */
    public File getAspectDataFile() {
        return aspectDataFile;
    }

    /**
     * Relative path of the build file within the workspace build directory.
     */
    public String getWorkspaceRelativePath() {
        return workspaceRelativePath;
    }

    /**
     * List of dependencies of the target.
     */
    public List<String> getDeps() {
        return deps;
    }

    /**
     * Kind of the target (e.g., java_test, java_binary, java_web_test_suite, etc).
     */
    public String getKind() {
        return kind;
    }

    /**
     * Label of the target.
     */
    public String getLabelPath() {
        return label;
    }

    /**
     * Label of the target.
     */
    public BazelLabel getLabel() {
        return new BazelLabel(label);
    }

    /**
     * List of sources consumed by this target.
     */
    public List<String> getSources() {
        return sources;
    }

    protected AspectTargetInfo(File aspectDataFile, String workspaceRelativePath, String kind, String label,
            List<String> deps, List<String> sources) {
        this.aspectDataFile = aspectDataFile;
        this.workspaceRelativePath = workspaceRelativePath;
        this.kind = kind;
        this.label = label;
        this.deps = deps;
        this.sources = sources;
    }

}
