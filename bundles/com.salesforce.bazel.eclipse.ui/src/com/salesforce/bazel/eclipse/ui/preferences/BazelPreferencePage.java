/*-
 * Copyright (c) 2019 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - initial version
*/
package com.salesforce.bazel.eclipse.ui.preferences;

import static java.lang.String.format;
import static java.nio.file.Files.isExecutable;
import static java.nio.file.Files.isRegularFile;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.preferences.BazelCorePreferenceKeys;
import com.salesforce.bazel.sdk.command.BazelBinaryVersionDetector;
import com.salesforce.bazel.sdk.util.SystemUtil;

/**
 * Page to configure the Bazel Eclipse plugin. See BazelPreferenceInitializer for how this preference is initialized
 * with a default value.
 */
public class BazelPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    private class BazelBinaryFieldEditor extends FileFieldEditor {
        BazelBinaryFieldEditor(Composite parent) {
            super(BazelCorePreferenceKeys.PREF_KEY_BAZEL_BINARY, "Path to the &Bazel binary:", false,
                    VALIDATE_ON_KEY_STROKE, parent);
        }

        @Override
        protected boolean doCheckState() {
            return isValid();
        }

        @Override
        public boolean isValid() {
            var bazelPath = getStringValue().trim();
            var binary = Path.of(bazelPath);

            if (useShellFieldEditor == null) {
                setErrorMessage("Incomplete initialization");
                return false;
            }

            if (!binary.isAbsolute() && !useShellFieldEditor.getBooleanValue()) {
                setErrorMessage(
                    "An absolute path to a Bazel binary must be given when the use of a Shell environment is disabled. Either enable use of Shell environment or specify an absolute path to Bazel binary.");
                return false;
            }

            if (binary.isAbsolute()) {
                if (!isRegularFile(binary)) {
                    setErrorMessage(bazelPath + " cannot be found.");
                    return false;
                }
                if (!isExecutable(binary)) {
                    setErrorMessage(bazelPath + " is not executable.");
                    return false;
                }
            }

            try {
                var bazelVersion =
                        new BazelBinaryVersionDetector(binary, useShellFieldEditor.getBooleanValue()).detectVersion();
                LOG.debug("Detected Bazel version for binary '{}': {}", binary, bazelVersion);
                setErrorMessage(null);
                return true;
            } catch (IOException e) {
                LOG.debug("IOException validating Bazel binary in preferences dialog", e);
                setErrorMessage(format("Unable to detect Bazel version of binary '%s': %s", binary, e.getMessage()));
                return false;
            } catch (InterruptedException e) {
                setErrorMessage("Interrupted waiting for bazel --version to respond for binary.");
                return false;
            }

        }
    }

    private class BazelUseShellFieldEditor extends BooleanFieldEditor {

        public BazelUseShellFieldEditor(Composite parent) {
            super(BazelCorePreferenceKeys.PREF_KEY_USE_SHELL_ENVIRONMENT,
                    "Use &shell environment when launching Bazel?", SEPARATE_LABEL, parent);
        }
    }

    private static Logger LOG = LoggerFactory.getLogger(BazelPreferencePage.class);

    private com.salesforce.bazel.eclipse.ui.preferences.BazelPreferencePage.BazelUseShellFieldEditor useShellFieldEditor;

    public BazelPreferencePage() {
        super(GRID);
        setDescription("Configure the Bazel Eclipe Feature.");
    }

    @Override
    public void createFieldEditors() {
        addField(new BazelBinaryFieldEditor(getFieldEditorParent()));

        // use Shell environment
        useShellFieldEditor = new BazelUseShellFieldEditor(getFieldEditorParent());

        // disallow changing value on windows
        useShellFieldEditor.setEnabled(!getSystemUtil().isWindows(), getFieldEditorParent());

        addField(useShellFieldEditor);
    }

    @Override
    protected IPreferenceStore doGetPreferenceStore() {
        // use the preference store of the Core plug-in
        // this implies that UI specific preferences should go into a separate page
        return PlatformUI.createPreferenceStore(BazelCorePlugin.class);
    }

    SystemUtil getSystemUtil() {
        return SystemUtil.getInstance();
    }

    @Override
    public void init(IWorkbench workbench) {
        // empty
    }
}
