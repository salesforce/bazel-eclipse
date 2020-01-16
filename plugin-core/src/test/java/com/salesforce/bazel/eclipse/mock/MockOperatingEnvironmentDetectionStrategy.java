package com.salesforce.bazel.eclipse.mock;

import com.salesforce.bazel.eclipse.model.OperatingEnvironmentDetectionStrategy;

public class MockOperatingEnvironmentDetectionStrategy implements OperatingEnvironmentDetectionStrategy {

    private String osName = "linux";
    
    public MockOperatingEnvironmentDetectionStrategy() {}
    public MockOperatingEnvironmentDetectionStrategy(String osName) {
        this.osName = osName;
    }
    
    @Override
    public String getOperatingSystemName() {
        return osName;
    }

}
