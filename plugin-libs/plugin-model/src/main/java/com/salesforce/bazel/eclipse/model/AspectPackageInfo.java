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

package com.salesforce.bazel.eclipse.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A parsed version of the JSON files returned by the application of the Bazel aspect.
 * <p>
 * The JSON document format is like this:
 *
 * <pre>
 &#64;org_slf4j_slf4j_api//jar:jar=AspectPackageInfo(
  label = @org_slf4j_slf4j_api//jar:jar,
  location = external/org_slf4j_slf4j_api/jar/BUILD.bazel,
  kind = java_import,
  jars = [Jars(jar = external/org_slf4j_slf4j_api/jar/slf4j-api-1.7.25.jar, ijar = bazel-out/darwin-fastbuild/genfiles/external/org_slf4j_slf4j_api/jar/_ijar/jar/external/org_slf4j_slf4j_api/jar/slf4j-api-1.7.25-ijar.jar)],
  generatedJars = [],
  deps = [],
  sources = []
 )
 * </pre>
 * <p>
 * See resources/bzleclipse_aspect.bzl for the code that creates the JSON files
 */
public final class AspectPackageInfo {

    public static final String ASPECT_FILENAME_SUFFIX = ".bzleclipse-build.json";
    private static final Joiner COMMA_JOINER = Joiner.on(",");

    private final File aspectDataFile; // full path to the file on the file system
    private final String workspaceRelativePath; // relative path on the filesystem within the workspace
    private final ImmutableList<String> deps;
    private final String kind;
    private final String label;
    private final String mainClass;

    private final ImmutableList<AspectOutputJars> generatedJars;
    private final ImmutableList<AspectOutputJars> jars;
    private final ImmutableList<String> sources;

    @Override
    public String toString() {
        StringBuffer builder = new StringBuffer();
        builder.append("AspectPackageInfo(\n");
        builder.append("  label = ").append(label).append(",\n");
        builder.append("  build_file_artifact_location = ").append(workspaceRelativePath).append(",\n");
        builder.append("  kind = ").append(kind).append(",\n");
        builder.append("  jars = [").append(COMMA_JOINER.join(jars)).append("],\n");
        builder.append("  generated_jars = [").append(COMMA_JOINER.join(generatedJars)).append("],\n");
        builder.append("  dependencies = [").append(COMMA_JOINER.join(deps)).append("],\n");
        builder.append("  sources = [").append(COMMA_JOINER.join(sources)).append("]),\n");
        builder.append("  main_class = ").append(mainClass).append("),\n");
        return builder.toString();
    }

    /**
     * Constructs a map of label -> @link AspectPackageInfo} from a list of file paths, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectPackageInfo} object.
     */
    @VisibleForTesting
    public static ImmutableMap<String, AspectPackageInfo> loadAspectFilePaths(List<String> aspectFilePaths)
            throws IOException, InterruptedException {

        List<File> fileList = new ArrayList<>();
        for (String aspectFilePath : aspectFilePaths) {
            if (!aspectFilePath.isEmpty()) {
                fileList.add(new File(aspectFilePath));
            }
        }

        return loadAspectFiles(fileList);
    }

    /**
     * Constructs a map of label -> {@link AspectPackageInfo} from a list of files, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectPackageInfo} object.
     */
    @VisibleForTesting
    public static ImmutableMap<String, AspectPackageInfo> loadAspectFiles(List<File> aspectFiles)
            throws IOException, InterruptedException {
        ImmutableMap.Builder<String, AspectPackageInfo> infos = ImmutableMap.builder();
        for (File aspectFile : aspectFiles) {
            AspectPackageInfo buildInfo = loadAspectFile(aspectFile);
            infos.put(buildInfo.label, buildInfo);
        }
        return infos.build();
    }

    /**
     * Constructs a map of label -> {@link AspectPackageInfo} from a list of files, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectPackageInfo} object.
     */
    @VisibleForTesting
    public static AspectPackageInfo loadAspectFile(File aspectFile) throws IOException, InterruptedException {
        AspectPackageInfo buildInfo = null;
        if (aspectFile.exists()) {
            JSONObject json = new JSONObject(new JSONTokener(new FileInputStream(aspectFile)));
            buildInfo = AspectPackageInfo.loadAspectFromJson(aspectFile, json);
        }
        return buildInfo;
    }

    /**
     * Location of the generated file that was used to build this instance.
     *
     * @return the File
     */
    public File getAspectDataFile() {
        return this.aspectDataFile;
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
     * Kind of the target (e.g., java_test or java_binary).
     */
    public String getKind() {
        return kind;
    }

    /**
     * Label of the target.
     */
    public String getLabel() {
        return label;
    }

    /**
     * List of jars generated by annotations processors when building this target.
     */
    public List<AspectOutputJars> getGeneratedJars() {
        return generatedJars;
    }

    /**
     * List of jars generated by building this target.
     */
    public List<AspectOutputJars> getJars() {
        return jars;
    }

    /**
     * List of sources consumed by this target.
     */
    public List<String> getSources() {
        return sources;
    }
    
    /**
     * The value of the "main_class" attribute of this target, may be null if this target
     * doesn't specify a main_class.
     */
    public String getMainClass() {
        return mainClass;
    }

    // INTERNAL

    static AspectPackageInfo loadAspectFromJson(File aspectDataFile, JSONObject object) {
        AspectPackageInfo info = null;
        
        try {
            ImmutableList<AspectOutputJars> jars = jsonToJarArray(object.getJSONArray("jars"));
            ImmutableList<AspectOutputJars> generated_jars = jsonToJarArray(object.getJSONArray("generated_jars"));
            String build_file_artifact_location = object.getString("build_file_artifact_location");
            String kind = object.getString("kind");
            String label = object.getString("label");
            ImmutableList<String> deps = jsonToStringArray(object.getJSONArray("dependencies"));
            ImmutableList<String> sources = jsonToStringArray(object.getJSONArray("sources"));
            String mainClass = object.has("main_class") ? object.getString("main_class") : null;
            
            info = new AspectPackageInfo(aspectDataFile, jars, generated_jars, build_file_artifact_location, kind, 
                label, deps, sources, mainClass);
        } catch (Exception anyE) {
            //System.err.println("Error parsing Bazel aspect info from file "+aspectDataFile.getAbsolutePath()+". Error: "+anyE.getMessage());
            throw anyE;
        }
        return info;
    }

    AspectPackageInfo(File aspectDataFile, ImmutableList<AspectOutputJars> jars,
            ImmutableList<AspectOutputJars> generatedJars, String workspaceRelativePath, String kind, String label,
            ImmutableList<String> deps, ImmutableList<String> sources, String mainClass) {
        this.aspectDataFile = aspectDataFile;
        this.jars = jars;
        this.generatedJars = generatedJars;
        this.workspaceRelativePath = workspaceRelativePath;
        this.kind = kind;
        this.label = label;
        this.deps = deps;
        this.sources = sources;
        this.mainClass = mainClass;
    }

    private static ImmutableList<AspectOutputJars> jsonToJarArray(JSONArray array) {
        ImmutableList.Builder<AspectOutputJars> builder = ImmutableList.builder();
        for (Object o : array) {
            builder.add(new AspectOutputJars((JSONObject) o));
        }
        return builder.build();
    }

    private static ImmutableList<String> jsonToStringArray(JSONArray array) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Object o : array) {
            builder.add(o.toString());
        }
        return builder.build();
    }

}
