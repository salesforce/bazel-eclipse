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

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;

/**
 * Page to configure the Bazel Eclipse plugin. The only configuration parameter is the path to the Bazel binary so this
 * page provides a file field to specify it.
 * <p>
 * See BazelPreferenceInitializer for how this preference is initialized with a default value.
 */
public class BazelPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public static final String BAZEL_PATH_PREF_NAME = "BAZEL_PATH";

    private static class BazelBinaryFieldEditor extends FileFieldEditor {
        BazelBinaryFieldEditor(Composite parent) {
            super(BAZEL_PATH_PREF_NAME, "Path to the &Bazel binary:", true, parent);
            setValidateStrategy(VALIDATE_ON_FOCUS_LOST);
        }

        @Override
        protected boolean doCheckState() {
            try {
                BazelPluginActivator.getBazelCommandFacade().checkBazelVersion(getTextControl().getText());
                return true;
            } catch (BazelCommandLineToolConfigurationException e) {
                setErrorMessage(e.getMessage());
                return false;
            }
        }
    }

    public BazelPreferencePage() {
        super(GRID);
    }

    public void createFieldEditors() {
        addField(new BazelBinaryFieldEditor(getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {
        setPreferenceStore(BazelPluginActivator.getInstance().getPreferenceStore());
        setDescription("Bazel plugin settings");
    }
}
