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

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IPreferenceNodeVisitor;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public class MockIEclipsePreferences implements IEclipsePreferences {

    private static final String UOE_MSG =
            "MockPreferences is pay as you go, you have hit a method that is not implemented.";

    public Map<String, String> strings = new TreeMap<>();
    public Map<String, Boolean> booleans = new TreeMap<>();

    public List<IPreferenceChangeListener> listeners = new ArrayList<>();

    // MOCKED METHODS

    @Override
    public void flush() throws BackingStoreException {}

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

    @Override
    public void addPreferenceChangeListener(IPreferenceChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removePreferenceChangeListener(IPreferenceChangeListener listener) {
        listeners.remove(listener);
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
        booleans.put(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return booleans.getOrDefault(key, Boolean.FALSE).booleanValue();
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
    public void accept(IPreferenceNodeVisitor visitor) throws BackingStoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
