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
package com.salesforce.bazel.eclipse.config;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.preferences.BazelPreferenceKeys;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;

public class EclipseBazelConfigurationManager implements BazelConfigurationManager {
    /**
     * Absolute path of the Bazel workspace root
     */
    private static final String BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY = "bazel.workspace.root";

    private final ResourceHelper resourceHelper;

    public EclipseBazelConfigurationManager(ResourceHelper resourceHelper) {
        this.resourceHelper = resourceHelper;
    }

    @Override
    public String getBazelExecutablePath() {
        IPreferenceStore prefsStore = this.resourceHelper.getPreferenceStore(BazelPluginActivator.getInstance());
        return prefsStore.getString(BazelPreferenceKeys.BAZEL_PATH_PREF_NAME);
    }

    @Override
    public void setBazelExecutablePathListener(BazelCommandManager bazelCommandManager) {
        IPreferenceStore prefsStore = this.resourceHelper.getPreferenceStore(BazelPluginActivator.getInstance());
        prefsStore.addPropertyChangeListener(new IPropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (event.getProperty().equals(BazelPreferenceKeys.BAZEL_PATH_PREF_NAME)) {
                    bazelCommandManager.setBazelExecutablePath(event.getNewValue().toString());
                }
            }
        });
    }

    @Override
    public String getBazelWorkspacePath() {
        IPreferenceStore prefsStore = this.resourceHelper.getPreferenceStore(BazelPluginActivator.getInstance());
        return prefsStore.getString(EclipseBazelConfigurationManager.BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY);
    }

    @Override
    public void setBazelWorkspacePath(String bazelWorkspacePath) {
        IPreferenceStore prefsStore = this.resourceHelper.getPreferenceStore(BazelPluginActivator.getInstance());
        prefsStore.setValue(EclipseBazelConfigurationManager.BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY, bazelWorkspacePath);
    }

    /**
     * Global search is the feature for doing type (e.g. Java class) searches across all dependencies in the Bazel
     * workspace, not just the dependencies of the imported packages.
     */
    @Override
    public boolean isGlobalClasspathSearchEnabled() {
        IPreferenceStore prefsStore = this.resourceHelper.getPreferenceStore(BazelPluginActivator.getInstance());
        return prefsStore.getBoolean(BazelPreferenceKeys.GLOBALCLASSPATH_SEARCH_PREF_NAME);
    }

}