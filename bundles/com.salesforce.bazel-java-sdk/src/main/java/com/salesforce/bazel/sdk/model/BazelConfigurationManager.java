package com.salesforce.bazel.sdk.model;

import com.salesforce.bazel.sdk.command.BazelCommandManager;

public interface BazelConfigurationManager {

    /**
     * Gets the file system absolute path to the Bazel executable
     */
    String getBazelExecutablePath();

    /**
     * Configure a listener for changes to the path (often by the user) to the Bazel executable
     */
    void setBazelExecutablePathListener(BazelCommandManager bazelCommandManager);

    /**
     * Gets the absolute path to the root of the Bazel workspace
     */
    String getBazelWorkspacePath();

    /**
     * Sets the absoluate path to the root of the Bazel workspace
     */
    void setBazelWorkspacePath(String bazelWorkspacePath);

    /**
     * Global search is the feature for doing type (e.g. Java class) searches across all dependencies in the Bazel
     * workspace, not just the dependencies of the imported packages.
     */
    boolean isGlobalClasspathSearchEnabled();

}