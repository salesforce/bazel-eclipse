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
package com.salesforce.b2eclipse.managers;

import java.util.Map;

import org.eclipse.jdt.ls.core.internal.handlers.MapFlattener;

@SuppressWarnings("restriction")
public final class B2EPreferncesManager {

    /**
     * Preference key to enable/disable bazel importer.
     */
    private static final String IMPORT_BAZEL_ENABLED = "java.import.bazel.enabled";

    /**
     * Preference key to change java classes src path for bazel importer.
     */
    private static final String BAZEL_SRC_PATH = "java.import.bazel.src.path";

    /**
     * Preference key to change java classes test path for bazel importer.
     */
    private static final String BAZEL_TEST_PATH = "java.import.bazel.test.path";

    /**
     * Default java class src path for bazel importer.
     */
    private static final String BAZEL_DEFAULT_SRC_PATH = "/src/main/java";

    /**
     * Default java class test path for bazel importer.
     */
    private static final String BAZEL_DEFAULT_TEST_PATH = "/src/test/java";

    private static volatile B2EPreferncesManager instance;

    private boolean importBazelEnabled;
    private String importBazelSrcPath;
    private String importBazelTestPath;

    private B2EPreferncesManager() {

    }

    public void setConfiguration(Map<String, Object> configuration) {
        importBazelEnabled = MapFlattener.getBoolean(configuration, IMPORT_BAZEL_ENABLED, false);
        importBazelSrcPath = MapFlattener.getString(configuration, BAZEL_SRC_PATH, BAZEL_DEFAULT_SRC_PATH);
        importBazelTestPath = MapFlattener.getString(configuration, BAZEL_TEST_PATH, BAZEL_DEFAULT_TEST_PATH);
    }

    public static B2EPreferncesManager getInstance() {
        B2EPreferncesManager localInstance = instance;

        if (localInstance == null) {
            synchronized (B2EPreferncesManager.class) {
                localInstance = instance;

                if (localInstance == null) {
                    localInstance = new B2EPreferncesManager();
                    instance = localInstance;
                }
            }
        }

        return localInstance;
    }

    public boolean isImportBazelEnabled() {
        return importBazelEnabled;
    }

    public String getImportBazelSrcPath() {
        return importBazelSrcPath;
    }

    public String getImportBazelTestPath() {
        return importBazelTestPath;
    }

}
