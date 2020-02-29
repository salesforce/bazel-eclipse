package com.salesforce.bazel.eclipse.config;

public class BazelProjectConstants {
    
    /**
     * We create a special Eclipse project to represent the Bazel root workspace (where the WORKSPACE file lives). 
     * This is the base name of the project. The actual name of the workspace is added to this string, like "Bazel Workspace (acme)"
     */
    public static final String BAZELWORKSPACE_PROJECT_BASENAME = "Bazel Workspace";

}
