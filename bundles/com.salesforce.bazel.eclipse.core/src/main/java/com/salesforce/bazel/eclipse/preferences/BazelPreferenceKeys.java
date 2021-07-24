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
package com.salesforce.bazel.eclipse.preferences;

import java.util.HashMap;
import java.util.Map;

import com.salesforce.bazel.sdk.command.BazelCommandManager;

/**
 * Names of the preference keys. Preferences
 */
public class BazelPreferenceKeys {
    public static Map<String, String> defaultValues = new HashMap<>();

    // ADDING A PREF?
    // If you want to support a new pref, add it in the appropriate section below. Then,
    // you should also add it to one of the arrays at the bottom for automated support of your pref
    // in the global pref file (~/.bazel/eclipse.properties)

    // *********************************************************************
    // USER FACING PREFS (visible on Prefs page)

    // path the the bazel executable
    public static final String BAZEL_PATH_PREF_NAME = "BAZEL_PATH";
    static {
        String defaultExecutablePath = BazelCommandManager.getDefaultBazelExecutablePath();
        defaultValues.put(BAZEL_PATH_PREF_NAME, defaultExecutablePath);
    }

    // Global classpath search allows BEF to index all jars associated with a Bazel Workspace which makes them
    // available for Open Type searches. These prefs enabled it, and override the default location(s) of where
    // to look for the local cache of downloaded jars.
    public static final String GLOBALCLASSPATH_SEARCH_PREF_NAME = "GLOBALCLASSPATH_SEARCH_ENABLED";
    public static final String EXTERNAL_JAR_CACHE_PATH_PREF_NAME = "EXTERNAL_JAR_CACHE_PATH";
    static {
        defaultValues.put(GLOBALCLASSPATH_SEARCH_PREF_NAME, "true");
    }

    // *********************************************************************
    // BREAK GLASS PREFS (emergency feature flags to disable certain features in case of issues)
    // Naming convention: these should all started with the token DISABLE_

    // We support Bazel workspaces in which the WORKSPACE file in the root is actually a soft link to the actual
    // file in a subdirectory. Due to the way the system Open dialog works, we have to do some sad logic to figure
    // out this is the case. This flag disables this feature, in case that logic causes problems for some users.
    // https://github.com/salesforce/bazel-eclipse/issues/164
    public static final String DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK = "DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK";
    static {
        defaultValues.put(DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK, "false");
    }

    // *********************************************************************
    // BEF DEVELOPER PREFS (for efficient repetitive testing of BEF)

    // The import wizard will be populated by this path if set, which saves time during repetitive testing of imports
    public static final String BAZEL_DEFAULT_WORKSPACE_PATH_PREF_NAME = "BAZEL_DEFAULT_WORKSPACE_PATH";

    // *********************************************************************
    // ARRAYS
    // Be sure to add your new pref name here, as that is how the global pref file gets loaded into Eclipse prefs

    // prefs that have string values
    public static final String[] ALL_STRING_PREFS = new String[] { BAZEL_PATH_PREF_NAME,
            EXTERNAL_JAR_CACHE_PATH_PREF_NAME, BAZEL_DEFAULT_WORKSPACE_PATH_PREF_NAME };

    // prefs that have boolean values
    public static final String[] ALL_BOOLEAN_PREFS =
            new String[] { GLOBALCLASSPATH_SEARCH_PREF_NAME, DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK };

}
