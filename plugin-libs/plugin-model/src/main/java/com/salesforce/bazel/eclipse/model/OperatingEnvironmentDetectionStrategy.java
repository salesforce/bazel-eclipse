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
    
}
