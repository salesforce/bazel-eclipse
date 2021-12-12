package com.salesforce.bazel.eclipse.runtime.impl;

import java.util.Objects;

import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.activator.Activator;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;

public class EclipePreferenceStoreHelper implements PreferenceStoreHelper {
    private static final LogHelper LOG = LogHelper.log(EclipePreferenceStoreHelper.class);
    private final String scope;

    public EclipePreferenceStoreHelper(String requiredScope) {
        scope = Objects.nonNull(requiredScope) ? requiredScope : Activator.getDefault().getBundle().getSymbolicName();
    }

    @Override
    public String getString(String key) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(scope);
        String value = prefs.get(key, null);
        if (Objects.isNull(value)) {
            prefs = DefaultScope.INSTANCE.getNode(scope);
            value = prefs.get(key, null);
        }
        return value;
    }

    @Override
    public boolean getBoolean(String key) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(scope);
        String value = prefs.get(key, null);
        if (Objects.isNull(value)) {
            prefs = DefaultScope.INSTANCE.getNode(scope);
            value = prefs.get(key, null);
        }
        return Objects.nonNull(value) ? Boolean.valueOf(value) : false;
    }

    @Override
    public void setValue(String key, String value) {
        try {
            IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(scope);
            prefs.put(key, value);
            prefs.flush();
        } catch (BackingStoreException e) {
            LOG.error("Preference storing failed", e);
        }
    }

    @Override
    public void setValue(String key, boolean value) {
        try {
            IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(scope);
            prefs.putBoolean(key, value);
            prefs.flush();
        } catch (BackingStoreException e) {
            LOG.error("Preference storing failed", e);
        }
    }

    @Override
    public void setDefaultValue(String key, boolean value) {
        try {
            IEclipsePreferences prefs = DefaultScope.INSTANCE.getNode(scope);
            prefs.putBoolean(key, value);
            prefs.flush();
        } catch (BackingStoreException e) {
            LOG.error("Preference storing failed", e);
        }
    }

    @Override
    public void setDefaultValue(String key, String value) {
        try {
            IEclipsePreferences prefs = DefaultScope.INSTANCE.getNode(scope);
            prefs.put(key, value);
            prefs.flush();
        } catch (BackingStoreException e) {
            LOG.error("Preference storing failed", e);
        }
    }

    @Override
    public void addListener(IPreferenceChangeListener listener) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(scope);
        prefs.addPreferenceChangeListener(listener);
    }

    @Override
    public void removeListener(IPreferenceChangeListener listener) {
        IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(scope);
        prefs.removePreferenceChangeListener(listener);
    }
}
