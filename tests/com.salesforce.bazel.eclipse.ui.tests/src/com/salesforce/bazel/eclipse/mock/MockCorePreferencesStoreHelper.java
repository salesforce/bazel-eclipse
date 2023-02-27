package com.salesforce.bazel.eclipse.mock;

import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;

import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;

public class MockCorePreferencesStoreHelper implements PreferenceStoreHelper {
    private final MockEclipse mockEclipse;

    public MockCorePreferencesStoreHelper(MockEclipse mockEclipse) {
        this.mockEclipse = mockEclipse;
    }

    @Override
    public String getString(String key) {
        return mockEclipse.getMockPrefs().get(key, null);
    }

    @Override
    public boolean getBoolean(String key) {
        return mockEclipse.getMockPrefs().getBoolean(key, false);
    }

    @Override
    public void setValue(String key, String value) {
        mockEclipse.getMockPrefs().put(key, value);
    }

    @Override
    public void setValue(String key, boolean value) {
        mockEclipse.getMockPrefs().putBoolean(key, value);
    }

    @Override
    public void setDefaultValue(String key, String value) {
        mockEclipse.getMockPrefs().put(key, value);
    }

    @Override
    public void setDefaultValue(String key, boolean value) {
        mockEclipse.getMockPrefs().putBoolean(key, value);
    }

    @Override
    public void addListener(IPreferenceChangeListener listener) {
        mockEclipse.getMockPrefs().addPreferenceChangeListener(listener);
    }

    @Override
    public void removeListener(IPreferenceChangeListener listener) {
        mockEclipse.getMockPrefs().removePreferenceChangeListener(listener);
    }
}
