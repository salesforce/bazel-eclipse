package com.salesforce.bazel.eclipse.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;

public class MockIPreferenceStore implements IPreferenceStore {

    private static final String UOE_MSG = "MockIPreferenceStore is pay as you go, you have hit a method that is not implemented."; 
    
    public Map<String, String> strings = new TreeMap<>();
    private List<IPropertyChangeListener> propChangeListeners = new ArrayList<>();

    // MOCKED METHODS

    @Override
    public void addPropertyChangeListener(IPropertyChangeListener listener) {
        propChangeListeners.add(listener);
    }

    @Override
    public String getString(String name) {
        return strings.get(name);
    }
    
    @Override
    public void setValue(String name, String value) {
        strings.put(name, value);
    }


    
    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.
    

    @Override
    public boolean contains(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void firePropertyChangeEvent(String name, Object oldValue, Object newValue) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean getBoolean(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean getDefaultBoolean(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public double getDefaultDouble(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public float getDefaultFloat(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public int getDefaultInt(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public long getDefaultLong(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getDefaultString(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public double getDouble(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public float getFloat(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public int getInt(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public long getLong(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isDefault(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean needsSaving() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void putValue(String name, String value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void removePropertyChangeListener(IPropertyChangeListener listener) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDefault(String name, double value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDefault(String name, float value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDefault(String name, int value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDefault(String name, long value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDefault(String name, String defaultObject) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setDefault(String name, boolean value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setToDefault(String name) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setValue(String name, double value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setValue(String name, float value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setValue(String name, int value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setValue(String name, long value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setValue(String name, boolean value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
