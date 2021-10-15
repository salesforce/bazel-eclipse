package com.salesforce.bazel.eclipse.mock;

import org.eclipse.jface.preference.IPreferenceStore;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreResourceHelper;

public class MockCoreResourceHelper implements PreferenceStoreResourceHelper {
    private final MockEclipse mockEclipse;

    public MockCoreResourceHelper(MockEclipse mockEclipse) {
        this.mockEclipse = mockEclipse;
    }

    @Override
    public IPreferenceStore getPreferenceStore(BazelPluginActivator activator) {
        return mockEclipse.getMockPrefsStore();
    }

}
