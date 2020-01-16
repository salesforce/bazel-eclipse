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
    public File computeBazelWorkspaceExecRoot();
    
    /**
     * Returns the output base of the current Bazel workspace.
     */    
    public File computeBazelWorkspaceOutputBase();

    /**
     * Returns the bazel-bin of the current Bazel workspace.
     */
    public File computeBazelWorkspaceBin();

    /**
     * Returns the explicitly set option in the workspace config files (.bazelrc et al)
     */
    public void populateBazelWorkspaceCommandOptions(BazelWorkspaceCommandOptions commandOptions);
}
