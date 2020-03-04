package com.salesforce.bazel.eclipse.model;

import java.io.File;

/**
 * Minimal representation of a Bazel Package location on the file system.
 * 
 * @see BazelLabel for a container that is also Bazel Target aware. 
 * @author stoens
 * @since March 2020
 */
public interface BazelPackageLocation {
    
    /**
     * Returns the name of this Bazel Package - this is name of the final directory in the path.
     * 
     * For example, if this Bazel Package is at the abs path ~/projects/bazel-workspace/a/b/c,
     * this method returns "c". 
     */
    String getBazelPackageNameLastSegment();
    
    /**
     * Returns the path of this Bazel Package, relative to the WORKSPACE root directory.
     * 
     * For example, if this Bazel Package is at the abs path ~/projects/bazel-workspace/a/b/c, 
     * this method returns a/b/c.
     */
    String getBazelPackageFSRelativePath();
    
    /**
     * Returns the abs path of the directory containing the WORKSPACE file for this Bazel Package.
     * 
     * For example, if this Bazel Package is at the abs path ~/projects/bazel-workspace/a/b/c,
     * this method return ~/projects/bazel-workspace.

     */
    File getWorkspaceRootDirectory();
    
    /**
     * True if this is the root Bazel Package that contains the WORKSPACE file.  
     */
    boolean isWorkspaceRoot();
}
