package com.salesforce.bazel.eclipse.mock;

import com.salesforce.bazel.sdk.model.OperatingEnvironmentDetectionStrategy;

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

    /**
     * When running inside a tool (like an IDE) we sometimes want to handle errors and try to soldier on
     * even if something went awry. In particular, situations where timing issues impact an operation, 
     * the operation may get rerun a little later and succeed. 
     * <p>
     * But when we are running tests we want to be strict and fail on failures. This boolean should be set to
     * true when we are running tests.
     */
    @Override
    public boolean isTestRuntime() {
        return true;
    }

}
