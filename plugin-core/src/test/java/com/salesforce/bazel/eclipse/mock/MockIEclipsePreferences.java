package com.salesforce.bazel.eclipse.mock;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IPreferenceNodeVisitor;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public class MockIEclipsePreferences implements IEclipsePreferences {

    private static final String UOE_MSG = "MockPreferences is pay as you go, you have hit a method that is not implemented."; 

    public Map<String, String> strings = new TreeMap<>();
    
    // MOCKED METHODS

    @Override
    public void flush() throws BackingStoreException {
    }

    @Override
    public String get(String key, String def) {
        return strings.getOrDefault(key, def);
    }

    @Override
    public String[] keys() throws BackingStoreException {
        return strings.keySet().toArray(new String[] {});
    }

    @Override
    public void put(String key, String value) {
        strings.put(key, value);
    }
    

    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.


    @Override
    public void remove(String key) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void clear() throws BackingStoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void putInt(String key, int value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public int getInt(String key, int def) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void putLong(String key, long value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public long getLong(String key, long def) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void putFloat(String key, float value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public float getFloat(String key, float def) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void putDouble(String key, double value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public double getDouble(String key, double def) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void putByteArray(String key, byte[] value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public byte[] getByteArray(String key, byte[] def) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String[] childrenNames() throws BackingStoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Preferences parent() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Preferences node(String pathName) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean nodeExists(String pathName) throws BackingStoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void removeNode() throws BackingStoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String name() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String absolutePath() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void sync() throws BackingStoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void addNodeChangeListener(INodeChangeListener listener) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void removeNodeChangeListener(INodeChangeListener listener) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void addPreferenceChangeListener(IPreferenceChangeListener listener) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void removePreferenceChangeListener(IPreferenceChangeListener listener) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void accept(IPreferenceNodeVisitor visitor) throws BackingStoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
