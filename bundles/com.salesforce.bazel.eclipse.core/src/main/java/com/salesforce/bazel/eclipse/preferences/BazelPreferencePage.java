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

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.salesforce.bazel.eclipse.BazelPluginActivator;

/**
 * Page to configure the Bazel Eclipse plugin. See BazelPreferenceInitializer for how this preference is initialized
 * with a default value.
 */
public class BazelPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private static class BazelBinaryFieldEditor extends FileFieldEditor {
        BazelBinaryFieldEditor(Composite parent) {
            super(BazelPreferenceKeys.BAZEL_PATH_PREF_NAME, "Path to the &Bazel binary:", true, VALIDATE_ON_KEY_STROKE,
                parent);
        }

        @Override
        protected boolean doCheckState() {
            return isValid();
        }

        @Override
        public boolean isValid() {
            try {
                String bazelPath = getStringValue().trim();
                File bazelExecutable = new File(bazelPath);
                if (!bazelExecutable.exists()) {
                    setErrorMessage(bazelPath + " does not exist");
                    return false;
                }
                if (!bazelExecutable.canExecute()) {
                    setErrorMessage(bazelPath + " is not an executable");
                    return false;
                }
                return true;
            } catch (Exception e) {
                setErrorMessage(e.getMessage());
                return false;
            }
        }
    }

    private static class BazelGlobalClasspathSearchEnabledFieldEditor extends BooleanFieldEditor {

        public BazelGlobalClasspathSearchEnabledFieldEditor(Composite parent) {
            super(BazelPreferenceKeys.GLOBALCLASSPATH_SEARCH_PREF_NAME,
                "Enable &global classpath search?", SEPARATE_LABEL, parent);
        }
    }

    private static class BazelExternalDownloadCachePathEditor extends DirectoryFieldEditor {
        BazelExternalDownloadCachePathEditor(Composite parent) {
            super(BazelPreferenceKeys.EXTERNAL_JAR_CACHE_PATH_PREF_NAME,
                "Optional: path to the local &cache of downloaded jar files:", parent);
        }

        @Override
        protected boolean doCheckState() {
            return isValid();
        }

        @Override
        public boolean isValid() {
            try {
                String cachePath = getStringValue();
                if (cachePath.isEmpty()) {
                    // a cache path is optional
                    return true;
                }
                File cachePathFile = new File(cachePath);
                if (!cachePathFile.exists()) {
                    setErrorMessage(cachePath + " does not exist");
                    return false;
                }
                return true;
            } catch (Exception e) {
                setErrorMessage(e.getMessage());
                return false;
            }
        }
    }

    /**
     * Preference to control the behavior of the project structure strategies during import. See
     * ProjectStructureStrategy for more information.
     */
    private static class ProjectStructureOptimizationEnabledFieldEditor extends BooleanFieldEditor {

        public ProjectStructureOptimizationEnabledFieldEditor(Composite parent) {
            super(BazelPreferenceKeys.PROJECTSTRUCTUREOPTIMIZATIONS_PREF_NAME,
                "Enable &project structure optimizations during import? (rare to disable)", SEPARATE_LABEL, parent);
        }
    }

    public BazelPreferencePage() {
        super(GRID);
    }

    @Override
    public void createFieldEditors() {
        addField(new BazelBinaryFieldEditor(getFieldEditorParent()));
        addField(new BazelGlobalClasspathSearchEnabledFieldEditor(getFieldEditorParent()));
        addField(new BazelExternalDownloadCachePathEditor(getFieldEditorParent()));
        addField(new ProjectStructureOptimizationEnabledFieldEditor(getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(BazelPluginActivator.getInstance().getPreferenceStore());
        setDescription("Configure the Bazel feature.");
    }
}
