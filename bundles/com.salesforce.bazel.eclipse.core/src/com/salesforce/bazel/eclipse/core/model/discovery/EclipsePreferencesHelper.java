package com.salesforce.bazel.eclipse.core.model.discovery;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * A helper to make internal protected Eclipse code accessible
 */
@SuppressWarnings("restriction")
class EclipsePreferencesHelper extends EclipsePreferences {

    public static void convertToPreferences(Properties table, Preferences targetPreferences)
            throws BackingStoreException {
        EclipsePreferences.convertFromProperties((EclipsePreferences) targetPreferences, table, true);
        makeDirty((EclipsePreferences) targetPreferences);
    }

    private static void makeDirty(EclipsePreferences targetPreferences) throws BackingStoreException {
        try {
            var makeDirty = EclipsePreferences.class.getDeclaredMethod("makeDirty");
            makeDirty.setAccessible(true);
            makeDirty.invoke(targetPreferences);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            throw new BackingStoreException("Unable to mark modified preferences for needing save!", e);
        }

    }
}