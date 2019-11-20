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
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.salesforce.bazel.eclipse.model.projectviews;

import java.util.List;

/**
 * TODO we do not support the projectview file yet
 * 
 * 
 * This is an interface to defined a project view from the IntelliJ plugin for Bazel (https://ij.bazel.build) so that
 * project view can be shared between IntelliJ and Eclipse users.
 *
 * <p>
 * See http://ij.bazel.build/docs/project-views.html for the specification of the project view. This project view
 * support only a subset relevant for the Eclipse plugin.
 */
public interface ProjectView {
    /**
     * List of directories defined in the {@code directories} section of the project view. These are the directories to
     * include as source directories.
     */
    public List<String> getDirectories();

    /**
     * List of targets to build defined in the {@code targets} section of the project view.
     */
    public List<String> getTargets();

    /**
     * Return a number (e.g. 7) giving the java version that the IDE should support (section {@code java_language_level}
     * of the project view).
     */
    public int getJavaLanguageLevel();

    /**
     * List of build flags to pass to Bazel defined in the {@code build_flags} section of the project view.
     */
    public List<String> getBuildFlags();

    /** Returns a builder to construct an project view object. */
    public static Builder builder() {
        return new Builder();
    }
}
