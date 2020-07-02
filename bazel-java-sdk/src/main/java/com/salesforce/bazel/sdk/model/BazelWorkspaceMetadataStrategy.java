package com.salesforce.bazel.sdk.model;

import java.io.File;
import java.util.List;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;

/**
 * Worker interface for a delegate that can retrieve metadata for the BazelWorkspace.
 * For example, the primary implementation uses 'bazel info' commands. During tests,
 * these methods are implemented with knowledge of the testing context. 
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
    
    /**
     * Return the result of the bazel query
     */
    public List<String> computeBazelQuery(String query);
}
