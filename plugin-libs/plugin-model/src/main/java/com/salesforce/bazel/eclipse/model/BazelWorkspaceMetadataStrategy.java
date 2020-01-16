package com.salesforce.bazel.eclipse.model;

import java.io.File;

/**
 * Worker interface for a delegate that can retrieve metadata for the BazelWorkspace.
 * For example, the primary implementation uses 'bazel info' commands.
 */
public interface BazelWorkspaceMetadataStrategy {

    /**
     * Returns the execution root of the current Bazel workspace.
     */   
    public File getBazelWorkspaceExecRoot();
    
    /**
     * Returns the output base of the current Bazel workspace.
     */    
    public File getBazelWorkspaceOutputBase();

    /**
     * Returns the bazel-bin of the current Bazel workspace.
     */
    public File getBazelWorkspaceBin();

}
