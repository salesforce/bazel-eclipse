package com.salesforce.bazel.eclipse.mock;

import java.util.Map;

import org.eclipse.core.resources.ICommand;

public class MockICommand implements ICommand {
    private static final String UOE_MSG = "MockICommand is pay as you go, you have hit a method that is not implemented."; 
    private String builderName;
    
    @Override
    public String getBuilderName() {
        return builderName;
    }

    @Override
    public void setBuilderName(String builderName) {
        this.builderName = builderName;
    }


    
    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.

    @Override
    public Map<String, String> getArguments() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isBuilding(int kind) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isConfigurable() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setArguments(Map<String, String> args) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setBuilding(int kind, boolean value) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
