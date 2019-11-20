package com.salesforce.bazel.eclipse.mock;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;

public class MockIScopeContext implements IScopeContext {

    private static final String UOE_MSG = "MockIScopeContext is pay as you go, you have hit a method that is not implemented."; 

    public IProject project = null;
    public Map<String, MockIEclipsePreferences> prefStore = new TreeMap<>();
    
    public MockIScopeContext(IProject project) {
        this.project = project;
    }
    
    // MOCKED METHODS

    @Override
    public IEclipsePreferences getNode(String qualifier) {
        return prefStore.get(qualifier);
    }

    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.

    @Override
    public String getName() {
        throw new UnsupportedOperationException(UOE_MSG);
    }


    @Override
    public IPath getLocation() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
