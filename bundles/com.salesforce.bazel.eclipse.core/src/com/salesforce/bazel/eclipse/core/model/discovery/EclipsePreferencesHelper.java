package com.salesforce.bazel.eclipse.core.model.discovery;

import java.util.Properties;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.osgi.service.prefs.Preferences;

/**
 * A helper to make internal protected Eclipse code accessible
 */
@SuppressWarnings("restriction")
class EclipsePreferencesHelper extends EclipsePreferences {

    public static void convertToPreferences(Properties table, Preferences targetPreferences) {
        EclipsePreferences.convertFromProperties((EclipsePreferences) targetPreferences, table, true);
    }
}