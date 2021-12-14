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
package com.salesforce.bazel.eclipse.preferences;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.util.BazelExecutableUtil;

/**
 * Initialize the preferences of Bazel plugins. See the BazelPreferenceKeys class for the supported preferences.
 */
public class BazelPreferenceInitializer extends AbstractPreferenceInitializer {
    @Override
    public void initializeDefaultPreferences() {
        Properties userDefaults = loadUserDefaultPreferencesFile();

        // STRING PREFS

        for (String prefName : BazelPreferenceKeys.ALL_STRING_PREFS) {
            String befDefaultValue = BazelPreferenceKeys.defaultValues.get(prefName);
            String value = userDefaults.getProperty(prefName, befDefaultValue);
            if (value != null) {
                ComponentContext.getInstance().getPreferenceStoreHelper().setDefaultValue(prefName, value);
            }
        }

        // BOOLEAN PREFS

        for (String prefName : BazelPreferenceKeys.ALL_BOOLEAN_PREFS) {
            String befDefaultValue = BazelPreferenceKeys.defaultValues.get(prefName);
            String value = userDefaults.getProperty(prefName, befDefaultValue);
            ComponentContext.getInstance().getPreferenceStoreHelper().setDefaultValue(prefName,
                Boolean.toString(true).equals(value));
        }

        // SPECIAL CASES

        String defaultBazelExecutablePath = BazelCommandManager.getDefaultBazelExecutablePath();
        String bazelExecLocationFromEnv = BazelExecutableUtil.which("bazel", defaultBazelExecutablePath);
        String value = userDefaults.getProperty(BazelPreferenceKeys.BAZEL_PATH_PREF_NAME, bazelExecLocationFromEnv);
        ComponentContext.getInstance().getPreferenceStoreHelper()
                .setDefaultValue(BazelPreferenceKeys.BAZEL_PATH_PREF_NAME, value);
    }

    /**
     * A user may place an eclipse.properties file in their ~/.bazel directory which will persist preferences to be used
     * for any new Eclipse workspace. This is a savior for those of us who work on BEF and create new Elipse workspaces
     * all the time. Might be useful for regular users too.
     */
    private Properties loadUserDefaultPreferencesFile() {
        Properties masterProperties = new Properties();
        String userHome = System.getProperty("user.home");
        File masterPropertiesFile = new File(userHome + File.separator + ".bazel", "eclipse.properties");
        if (masterPropertiesFile.exists()) {
            try (FileReader fileReader = new FileReader(masterPropertiesFile)) {
                masterProperties.load(fileReader);
            } catch (Exception anyE) {}
        }

        return masterProperties;
    }

}
