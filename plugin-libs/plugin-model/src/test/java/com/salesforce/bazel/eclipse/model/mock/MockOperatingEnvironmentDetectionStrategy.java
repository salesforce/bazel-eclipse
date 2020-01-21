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

}
