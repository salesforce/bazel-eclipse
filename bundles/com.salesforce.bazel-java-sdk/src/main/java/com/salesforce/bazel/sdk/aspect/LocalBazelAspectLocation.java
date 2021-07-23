package com.salesforce.bazel.sdk.aspect;

import java.io.File;

/**
 * Provides a local file system location of the aspect to use to analyze a Bazel workspace.
 */
public class LocalBazelAspectLocation implements BazelAspectLocation {
    private File aspectDirectory;

    public LocalBazelAspectLocation(File aspectDirectory) {
        this.aspectDirectory = aspectDirectory;
    }

    /**
     * Returns a {@link File} object that points to the Bazel directory containing the aspect bzl file. See implementor
     * of this interface for details.
     */
    @Override
    public File getAspectDirectory() {
        return this.aspectDirectory;
    }

    /**
     * Returns the label of the aspect in the Bazel workspace (with the function name).
     * <p>
     * For example: "//:bzljavasdk_aspect.bzl%bzljavasdk_aspect"
     */
    @Override
    public String getAspectLabel() {
        return "//:bzljavasdk_aspect.bzl%bzljavasdk_aspect";
    }

}
