/**
 * Copyright (c) 2022, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.lang.jvm.classpath.persist;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;

/**
 * Serialization and deserialization of the classpath as json.
 */
public class JvmClasspathSerializer {
    // Follow the style guide:
    // https://google.github.io/styleguide/jsoncstyleguide.xml
    
    public JvmClasspathSerializer() {
        
    }

    /**
     * Serializes the JvmClasspathData to json. 
     * <p>
     * Caller can provide an arbitrary extra payload object, which can contains metadata about the classpath. 
     * For example, you can put a hash of the Bazel configuration such that you can determine when the classpath 
     * needs to be recomputed.
     */
    public JSONObject serializeToJson(JvmClasspathData classpathData, String extraPayloadName, JSONObject extraPayload) {
        JSONObject body = new JSONObject();

        if (extraPayload != null) {
            body.put(extraPayloadName, extraPayload);
        }

        JSONObject classpathJson = serializeToJson(classpathData);
        body.put("classpath", classpathJson);
        
        return body;
    }
    
    /**
     * Serializes the JvmClasspathData to json as a JSONObject.
     */
    public JSONObject serializeToJson(JvmClasspathData classpathData) {
        JSONArray mainDeps = new JSONArray();
        JSONArray runtimeDeps = new JSONArray();
        JSONArray testDeps = new JSONArray();
        
        for (JvmClasspathEntry entry : classpathData.jvmClasspathEntries) {
            serializeJarEntryToJson(entry, mainDeps, runtimeDeps, testDeps);
        }
        
        JSONObject body = new JSONObject();
        body.put("deps", mainDeps);
        body.put("runtimeDeps", runtimeDeps);
        body.put("testDeps", testDeps);
        
        // TODO project refs
        
        return body;
    }

    /**
     * Deserializes JvmClasspathData from a json String
     */
    public JvmClasspathData deserializeFromJson(String bodyText) {
        JSONParser parser = new JSONParser();
        JSONObject body = null;
        try {
            body = (JSONObject)parser.parse(bodyText);
        } catch (ParseException pe) {
            pe.printStackTrace();
            return null;
        }
        return deserializeFromJson(body);
    }

    /**
     * Deserializes JvmClasspathData from a json Reader
     */
    public JvmClasspathData deserializeFromJson(Reader bodyReader) {
        JSONParser parser = new JSONParser();
        JSONObject body = null;
        try {
            body = (JSONObject)parser.parse(bodyReader);
        } catch (Exception pe) {
            pe.printStackTrace();
            return null;
        }
        return deserializeFromJson(body);
    }

    /**
     * Deserializes JvmClasspathData from a parsed JSONObject
     */
    public JvmClasspathData deserializeFromJson(JSONObject body) {
        JvmClasspathData classpathData = new JvmClasspathData();
        List<JvmClasspathEntry> results = new ArrayList<>();
        
        JSONArray deps = (JSONArray)body.get("deps");
        deserializeJarEntriesFromJson(deps, results, false, false);
        JSONArray runtimeDeps = (JSONArray)body.get("runtimeDeps");
        deserializeJarEntriesFromJson(runtimeDeps, results, true, false);
        JSONArray testDeps = (JSONArray)body.get("testDeps");
        deserializeJarEntriesFromJson(testDeps, results, false, true);
        
        classpathData.jvmClasspathEntries = results.toArray(new JvmClasspathEntry[] {});
        
        // TODO project refs
        
        classpathData.isComplete = true;
        
        return classpathData;
    }
    
    // INTERNAL
    
    protected void serializeJarEntryToJson(JvmClasspathEntry entry,
            JSONArray mainDeps, JSONArray runtimeDeps, JSONArray testDeps) {
        JSONObject entryJson = new JSONObject();
        entryJson.put("path", entry.pathToJar);
        entryJson.put("srcpath", entry.pathToSourceJar);
        
        if (entry.isTestJar) {
            testDeps.add(entryJson);
        } else if (entry.isRuntimeJar) {
            runtimeDeps.add(entryJson);
        } else {
            mainDeps.add(entryJson);
        }
    }
    
    protected void deserializeJarEntriesFromJson(JSONArray deps, List<JvmClasspathEntry> results, 
            boolean isRuntimeDep, boolean isTestDep) {
        if (deps != null) {
            for (Object entryObj : deps) {
                JSONObject entryJsonObj = (JSONObject)entryObj;
                JvmClasspathEntry entry = deserializeJarEntryFromJson(entryJsonObj, isRuntimeDep, isTestDep);
                if (entry != null) {
                    results.add(entry);
                }
            }
        }
    }
    
    protected JvmClasspathEntry deserializeJarEntryFromJson(JSONObject entryObj, boolean isRuntimeDep, boolean isTestDep) {
        JvmClasspathEntry entry = null;
        
        // minimum data is the path, srcpath is optional (this can be missing if someone hand edits the serialized file)
        String path = (String)entryObj.get("path");
        if (path != null) {
            String srcpath = (String)entryObj.get("srcpath");
            entry = new JvmClasspathEntry(path, srcpath, isRuntimeDep, isTestDep);
        }
        
        return entry;
    }

}
