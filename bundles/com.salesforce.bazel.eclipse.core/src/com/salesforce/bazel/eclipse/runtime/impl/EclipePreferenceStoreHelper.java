package com.salesforce.bazel.eclipse.runtime.impl;

import java.util.Objects;

import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;

public class EclipePreferenceStoreHelper implements PreferenceStoreHelper {
    private final String scope;

    public EclipePreferenceStoreHelper(String requiredScope) {
        scope = Objects.nonNull(requiredScope) ? requiredScope : BazelCoreSharedContstants.PLUGIN_ID;
    }

    @Override
    public void addListener(IPreferenceChangeListener listener) {
        var prefs = InstanceScope.INSTANCE.getNode(scope);
        prefs.addPreferenceChangeListener(listener);
    }

    @Override
    public boolean getBoolean(String key) {
        var prefs = InstanceScope.INSTANCE.getNode(scope);
        var value = prefs.get(key, null);
        if (Objects.isNull(value)) {
            prefs = DefaultScope.INSTANCE.getNode(scope);
            value = prefs.get(key, null);
        }
        return Objects.nonNull(value) ? Boolean.parseBoolean(value) : false;
    }

    @Override
    public String getString(String key) {
        var prefs = InstanceScope.INSTANCE.getNode(scope);
        var value = prefs.get(key, null);
        if (Objects.isNull(value)) {
            prefs = DefaultScope.INSTANCE.getNode(scope);
            value = prefs.get(key, null);
        }
        return value;
    }

    @Override
    public void removeListener(IPreferenceChangeListener listener) {
        var prefs = InstanceScope.INSTANCE.getNode(scope);
        prefs.removePreferenceChangeListener(listener);
    }

    @Override
    public void setDefaultValue(String key, boolean value) {
        try {
            var prefs = DefaultScope.INSTANCE.getNode(scope);
            prefs.putBoolean(key, value);
            prefs.flush();
        } catch (BackingStoreException e) {
            // default scope is in-memory
        }
    }

    @Override
    public void setDefaultValue(String key, String value) {
        try {
            var prefs = DefaultScope.INSTANCE.getNode(scope);
            prefs.put(key, value);
            prefs.flush();
        } catch (BackingStoreException e) {
            // default scope is in-memory
        }
    }

    @Override
    public void setValue(String key, boolean value) {
        try {
            var prefs = InstanceScope.INSTANCE.getNode(scope);
            prefs.putBoolean(key, value);
            prefs.flush();
        } catch (BackingStoreException e) {
            throw new IllegalStateException("Error saving preferences: " + e.getMessage(), e);
        }
    }

    @Override
    public void setValue(String key, String value) {
        try {
            var prefs = InstanceScope.INSTANCE.getNode(scope);
            prefs.put(key, value);
            prefs.flush();
        } catch (BackingStoreException e) {
            throw new IllegalStateException("Error saving preferences: " + e.getMessage(), e);
        }
    }
}
