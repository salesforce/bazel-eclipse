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
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfoFactoryProvider;

/**
 * Factory for AspectTargetInfo instances, using the JSON emitted from the aspect. Each rule type will have a different
 * JSON format, and therefore the factory is specific to a rule type (e.g. java_library).
 */
public class AspectTargetInfoFactory {

    private static Logger LOG = LoggerFactory.getLogger(AspectTargetInfoFactory.class);
    public static final String ASPECT_FILENAME_SUFFIX = ".bzljavasdk-data.json";

    protected static List<AspectTargetInfoFactoryProvider> providers = new ArrayList<>();
    static {
        providers.add(new JVMAspectTargetInfoFactoryProvider());
    }

    /**
     * During initialization, add providers that can parse target specific json in the apsect files.
     */
    public static void addProvider(AspectTargetInfoFactoryProvider provider) {
        providers.add(provider);
    }

    /**
     * Constructs a map of label -> {@link AspectTargetInfo} from a list of files, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectTargetInfo} object.
     */
    public static AspectTargetInfo loadAspectFile(Path aspectFile) {
        AspectTargetInfo targetInfo = null;
        JSONParser jsonParser = new JSONParser();

        if (aspectFile.exists()) {
            new Gson().fromJson(Files.newBufferedReader(aspectFile), AspectTargetInfo.class);
            targetInfo = loadAspectFromJson(aspectFile, jsonObject, jsonParser);
            if (targetInfo != null) {
                LOG.info("Loaded aspect for target {} from file {}", targetInfo.label,
                    targetInfo.aspectDataFile.getAbsolutePath());
            }
        } else {
            LOG.error("Aspect JSON file {} is missing.", aspectFile.getAbsolutePath());
        }
        return targetInfo;
    }

    /**
     * Constructs a map of label -> @link AspectTargetInfo} from a list of file paths, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectTargetInfo} object.
     */
    public static Map<String, AspectTargetInfo> loadAspectFilePaths(List<String> aspectFilePaths)
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
     * Constructs a map of label -> {@link AspectTargetInfo} from a list of files, parsing each files into a
     * {@link JSONObject} and then converting that {@link JSONObject} to an {@link AspectTargetInfo} object.
     */
    public static Map<String, AspectTargetInfo> loadAspectFiles(List<File> aspectFiles) {
        Map<String, AspectTargetInfo> infos = new HashMap<>();
        for (File aspectFile : aspectFiles) {
            AspectTargetInfo buildInfo = loadAspectFile(aspectFile);
            if (buildInfo == null) {
                // bug in the aspect parsing code
                LOG.error("The aspect file could not be parsed for aspect path {}", aspectFile.getAbsolutePath());
                continue;
            }

            var labelPath = buildInfo.getLabelPath();
            if (labelPath != null) {
                infos.put(labelPath, buildInfo);
            } else {
                // bug in the aspect parsing code
                LOG.error("Bug in the aspect parsing code, the label is null for package path {} for aspect file {}",
                    buildInfo.workspaceRelativePath, aspectFile.getAbsolutePath());
            }
        }
        return infos;
    }

    // INTERNAL

    static AspectTargetInfo loadAspectFromJson(File aspectDataFile, JSONObject aspectObject, JSONParser jsonParser) {
        AspectTargetInfo info = null;

        try {
            List<String> deps = loadDeps(aspectObject);

            var build_file_artifact_location = "test";//aspectObject.getString("build_file_artifact_location"); // TODO this field should be parsed correctly
            var kind = (String) aspectObject.get("kind_string");
            if (kind == null) {
                LOG.error(
                    "Aspect file {} is missing the kind_string property; this is likely a data file created by an older version of the aspect",
                    aspectDataFile);
            }
            String label = loadLabel(aspectObject);

            for (AspectTargetInfoFactoryProvider provider : providers) {
                info = provider.buildAspectTargetInfo(aspectDataFile, aspectObject, jsonParser,
                    build_file_artifact_location, kind, label, deps);
                if (info != null) {
                    break;
                }
            }
            if (info == null) {
                LOG.info("Could not find an AspectTargetInfoFactoryProvider for rule kind {}", kind);
            }
        } catch (Exception anyE) {
            //System.err.println("Error parsing Bazel aspect info from file "+aspectDataFile.getAbsolutePath()+". Error: "+anyE.getMessage());
            throw new IllegalArgumentException(anyE);
        }
        return info;
    }

    private static List<String> loadDeps(JSONObject aspectObject) throws Exception {
        List<String> list = new ArrayList<>();
        if (aspectObject == null) {
            return list;
        }
        JSONArray depArray = (JSONArray) aspectObject.get("deps");
        if (depArray != null) {
            for (Object dep : depArray) {
                JSONObject depObj = (JSONObject) dep;
                JSONObject targetObj = (JSONObject) depObj.get("target");
                if (targetObj != null) {
                    Object labelObj = targetObj.get("label");
                    if (labelObj != null) {
                        list.add(labelObj.toString());
                    }
                }
            }
        }
        return list;
    }

    private static String loadLabel(JSONObject aspectObject) throws Exception {
        String label = null;

        JSONObject keyObj = (JSONObject) aspectObject.get("key");
        if (keyObj != null) {
            Object labelObj = keyObj.get("label");
            if (labelObj != null) {
                label = labelObj.toString();
            }
        }

        return label;
    }

}
