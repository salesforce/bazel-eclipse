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

import com.salesforce.bazel.sdk.project.BazelProject;

/**
 * Entry in a classpath for the JVM that points to a Jar with bytecode.
 */
public class JvmClasspathEntry implements Comparable<JvmClasspathEntry> {

    // TODO make classpath entries better typed (jar, project)

    // Jar Entry
    public String pathToJar;
    public String pathToSourceJar;
    public boolean isTestJar;

    // Project Entry
    public BazelProject bazelProject;

    public JvmClasspathEntry(String pathToJar, boolean isTestJar) {
        this.pathToJar = pathToJar;
        this.isTestJar = isTestJar;
    }

    public JvmClasspathEntry(String pathToJar, String pathToSourceJar, boolean isTestJar) {
        this.pathToJar = pathToJar;
        this.pathToSourceJar = pathToSourceJar;
        this.isTestJar = isTestJar;
    }

    public JvmClasspathEntry(BazelProject bazelProject) {
        this.bazelProject = bazelProject;
    }

    @Override
    public int compareTo(JvmClasspathEntry otherEntry) {
        return pathToJar.compareTo(otherEntry.pathToJar);
    }
}
