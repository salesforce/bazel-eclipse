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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;

import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;

public class JvmClasspathSerializerTest {

    @Test
    public void testSerialize() {
        JvmClasspathSerializer serializer = new JvmClasspathSerializer();
        JvmClasspathData mockClasspath = createMockData();

        JSONObject body = serializer.serializeToJson(mockClasspath);

        verifyMockDataJson(body);
    }

    @Test
    public void testRoundTrip() {
        JvmClasspathSerializer serializer = new JvmClasspathSerializer();
        JvmClasspathData mockClasspath = createMockData();

        JSONObject body = serializer.serializeToJson(mockClasspath);
        JvmClasspathData deserialized = serializer.deserializeFromJson(body);
        assertTrue(deserialized.isComplete);

        JSONObject body2 = serializer.serializeToJson(deserialized);

        verifyMockDataJson(body2);
    }

    // HELPERS

    void verifyMockDataJson(JSONObject body) {
        JSONArray mainDeps = (JSONArray) body.get("deps");
        assertEquals(mainDeps.size(), 1);
        verifyEntry((JSONObject) mainDeps.get(0), "/a/b/c/aaa.jar", "/a/b/c/aaa-src.jar");

        JSONArray runtimeDeps = (JSONArray) body.get("runtimeDeps");
        assertEquals(runtimeDeps.size(), 1);
        verifyEntry((JSONObject) runtimeDeps.get(0), "/a/b/c/bbb.jar", "/a/b/c/bbb-src.jar");

        JSONArray testDeps = (JSONArray) body.get("testDeps");
        assertEquals(testDeps.size(), 1);
        verifyEntry((JSONObject) testDeps.get(0), "/a/b/c/ccc.jar", "/a/b/c/ccc-src.jar");
    }

    void verifyEntry(JSONObject entry, String path, String srcpath) {
        assertEquals(path, entry.get("path"));
        assertEquals(srcpath, entry.get("srcpath"));
    }

    JvmClasspathData createMockData() {
        JvmClasspathData classpathData = new JvmClasspathData();
        classpathData.jvmClasspathEntries = new JvmClasspathEntry[3];

        classpathData.jvmClasspathEntries[0] =
                new JvmClasspathEntry("/a/b/c/aaa.jar", "/a/b/c/aaa-src.jar", false, false); // main
        classpathData.jvmClasspathEntries[1] =
                new JvmClasspathEntry("/a/b/c/bbb.jar", "/a/b/c/bbb-src.jar", true, false); //runtime
        classpathData.jvmClasspathEntries[2] =
                new JvmClasspathEntry("/a/b/c/ccc.jar", "/a/b/c/ccc-src.jar", false, true); // test

        return classpathData;
    }
}
