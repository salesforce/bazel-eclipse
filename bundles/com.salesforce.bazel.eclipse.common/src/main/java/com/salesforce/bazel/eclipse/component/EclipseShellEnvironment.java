package com.salesforce.bazel.eclipse.component;

import com.salesforce.bazel.eclipse.preferences.BazelPreferenceKeys;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;
import com.salesforce.bazel.sdk.command.shell.ShellEnvironment;

public class EclipseShellEnvironment implements ShellEnvironment {
    private final PreferenceStoreHelper eclipsePrefsHelper;

    EclipseShellEnvironment(PreferenceStoreHelper eclipsePrefsHelper) {
        this.eclipsePrefsHelper = eclipsePrefsHelper;
    }

    @Override
    public boolean launchWithBashEnvironment() {
        // TODO check for OS
        return eclipsePrefsHelper.getBoolean(BazelPreferenceKeys.BAZEL_USE_SHELL_ENVIRONMENT_PREF_NAME);
    }
}