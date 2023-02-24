/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.eclipse.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;

public class MockIPreferenceStore implements IPreferenceStore {

    private static final String UOE_MSG =
            "MockIPreferenceStore is pay as you go, you have hit a method that is not implemented.";

    public Map<String, String> strings = new TreeMap<>();
    public Map<String, Boolean> booleans = new TreeMap<>();
    private final List<IPropertyChangeListener> propChangeListeners = new ArrayList<>();

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

    @Override
    public boolean getBoolean(String name) {
        Boolean result = booleans.get(name);
        if (result == null) {
            // by Eclipse definition, a boolean pref has a default value of false
            result = false;
        }
        return result;
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
