package com.salesforce.bazel.eclipse.model;

/**
 * Isolates the code that looks up operating environment data so that it can be
 * mocked for tests.
 *
 */
public interface OperatingEnvironmentDetectionStrategy {

    /**
     * Returns the operating system running Bazel and our BEF: osx, linux, windows
     * https://github.com/bazelbuild/bazel/blob/c35746d7f3708acb0d39f3082341de0ff09bd95f/src/main/java/com/google/devtools/build/lib/util/OS.java#L21
     */
    String getOperatingSystemName();
    
    /**
     * Returns the OS identifier used in file system constructs: darwin, linux, windows
     */
    default String getOperatingSystemDirectoryName(String osName) {
        String operatingSystemFoldername = null;
        if (osName.contains("mac")) {
            operatingSystemFoldername = "darwin";
        } else if (osName.contains("win")) {
            operatingSystemFoldername = "windows";
        } else {
            // assume linux
            operatingSystemFoldername = "linux";
        }
        return operatingSystemFoldername;
    }
    
    
    /**
     * When running inside a tool (like an IDE) we sometimes want to handle errors and try to soldier on
     * even if something went awry. In particular, situations where timing issues impact an operation, 
     * the operation may get rerun a little later and succeed. 
     * <p>
     * But when we are running tests we want to be strict and fail on failures. This boolean should be set to
     * true when we are running tests.
     */
    default boolean isTestRuntime() {
        return false;
    }

}
