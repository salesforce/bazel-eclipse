/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.lang.jvm.classpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.project.BazelProjectOld;

/**
 * Carries the data from a classpath computation.
 */
public class JvmClasspathData {

    /**
     * Marks if this data comes from a successful computation of the classpath. If the underlying mechanism used to
     * compute the classpath failed, this will remain false.
     */
    public boolean isComplete = false;

    /**
     * The jvm classpath entries (e.g. jar files)
     */
    public JvmClasspathEntry[] jvmClasspathEntries = {};

    /**
     * The list of projects that should be added to the classpath, if this environment is using project support. The
     * caller is expected to invoke the following: bazelProjectManager.setProjectReferences(bazelProject,
     * computedClasspath.classpathProjectReferences); But due to locking in some environments, this may need to be
     * delayed.
     */
    public List<BazelProjectOld> classpathProjectReferences = new ArrayList<>();

    // INTERNAL

    // Indices to help during computation of what is on the test classpath, versus main+runtime.
    // key: path to the jar file, e.g. external/maven/v1/https/repo1.maven.org/maven2/com.google.guava/guava/20.0/guava-20.0.jar

    /**
     * Internal. Used during computation of what is on the test classpath, versus main+runtime.
     */
    public Map<String, JvmClasspathEntry> mainClasspathEntryMap = new TreeMap<>();

    /**
     * Internal. Used during computation of what is on the test classpath, versus main+runtime.
     */
    public Map<String, JvmClasspathEntry> testClasspathEntryMap = new TreeMap<>();

    /**
     * Internal. Set for the list of implicitDeps that will be added to the test classpath (this may be empty if
     * implicit deps are disabled for the workspace). This will be added by the classpath engine to the
     * jvmClasspathEntries as needed, so this member is not intended for use by clients.
     */
    public Set<JvmClasspathEntry> implicitDeps = Collections.emptySet();

}