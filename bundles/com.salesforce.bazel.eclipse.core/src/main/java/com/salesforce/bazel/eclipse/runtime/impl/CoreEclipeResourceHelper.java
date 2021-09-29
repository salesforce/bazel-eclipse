package com.salesforce.bazel.eclipse.runtime.impl;

import org.eclipse.jface.preference.IPreferenceStore;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.runtime.api.CoreResourceHelper;

public class CoreEclipeResourceHelper extends EclipseResourceHelper implements CoreResourceHelper {

    @Override
    public IPreferenceStore getPreferenceStore(BazelPluginActivator activator) {
        return activator.getPreferenceStore();
    }
}
