package com.salesforce.bazel.eclipse.runtime.api;

import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;

/**
 *
 * @deprecated such mocking is not needed - we should do proper Eclipse integration tests with SWT Bot and full
 *             OSGi/Eclipse environment
 */
@Deprecated
public interface PreferenceStoreHelper {
    String getString(String key);

    boolean getBoolean(String key);

    void setValue(String key, String value);

    void setValue(String key, boolean value);

    void addListener(IPreferenceChangeListener listener);

    void removeListener(IPreferenceChangeListener listener);

    void setDefaultValue(String key, String value);

    void setDefaultValue(String key, boolean value);
}
