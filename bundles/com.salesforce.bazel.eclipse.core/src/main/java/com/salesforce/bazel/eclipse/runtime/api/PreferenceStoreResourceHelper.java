package com.salesforce.bazel.eclipse.runtime.api;

import org.eclipse.jface.preference.IPreferenceStore;

import com.salesforce.bazel.eclipse.BazelPluginActivator;

public interface PreferenceStoreResourceHelper {

    /**
     * Gets the preference store for the Core plugin.
     */
    IPreferenceStore getPreferenceStore(BazelPluginActivator activator);

}
