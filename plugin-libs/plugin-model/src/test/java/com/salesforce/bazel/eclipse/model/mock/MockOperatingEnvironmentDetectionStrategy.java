package com.salesforce.bazel.eclipse.model.mock;

import com.salesforce.bazel.eclipse.model.OperatingEnvironmentDetectionStrategy;

/**
 * For tests, you can hardcode the answer to the os name computation. The os name is used in some cases
 * in the BEF.
 */
public class MockOperatingEnvironmentDetectionStrategy implements OperatingEnvironmentDetectionStrategy {

    public String osName = null;
    
    /**
     * Valid osNames: mac, win, linux
     */
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
    public boolean isTestRuntime() {
        return true;
    }

}
