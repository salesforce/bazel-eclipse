package com.salesforce.bazel.sdk.workspace;

/**
 * Real implementation of OperatingEnvironmentDetectionStrategy to be used when running in a tool.
 * It looks at system properties to determine the operating environment.
 *
 */
public class RealOperatingEnvironmentDetectionStrategy implements OperatingEnvironmentDetectionStrategy {

    @Override
    public String getOperatingSystemName() {
        String osName = System.getProperty("blaze.os");
        if (osName == null) {
            osName = System.getProperty("os.name", "unknown");
        }
        osName = osName.toLowerCase();
        
        return osName;
    }

}
