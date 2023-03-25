package com.salesforce.bazel.sdk.model;

import java.io.File;
import java.util.List;

/**
 * Minimal representation of a Bazel Package location on the file system.
 *
 * @see BazelLabel for a container that is also Bazel Target aware.
 */
public interface BazelPackageLocation {

    /**
     * Builds a list containing this node, plus all children (recursively)
     */
    List<BazelPackageLocation> gatherChildren();

    /**
     * Builds a list containing this node, plus all children (recursively). The pathFilter must be in the form
     * "projects/libs/foo" and will limit the gathering to that path and descendents of that path.
     * <p>
     * TODO convert pathFilter to a BazelLabel; SDK Issue #37
     */
    List<BazelPackageLocation> gatherChildren(String pathFilter);

    /**
     * Returns the path of this Bazel Package, relative to the WORKSPACE root directory.
     *
     * For example, if this Bazel Package is at the abs path ~/projects/bazel-workspace/a/b/c, this method returns
     * a/b/c.
     */
    String getBazelPackageFSRelativePath();

    /**
     * Provides the proper Bazel label for the Bazel package.
     * <p>
     *
     * e.g. "//projects/libs/apple"
     */
    String getBazelPackageName();

    /**
     * Returns the name of this Bazel Package - this is name of the final directory in the path.
     *
     * For example, if this Bazel Package is at the abs path ~/projects/bazel-workspace/a/b/c, this method returns "c".
     */
    String getBazelPackageNameLastSegment();

    /**
     * Returns the targets configured for this Bazel Package, at import time.
     *
     * A null return value indicates that the user did not specify any specific targets.
     */
    default List<BazelLabel> getBazelTargets() {
        return null;
    }

    /**
     * Returns the abs path of the directory containing the WORKSPACE file for this Bazel Package.
     *
     * For example, if this Bazel Package is at the abs path ~/projects/bazel-workspace/a/b/c, this method return
     * ~/projects/bazel-workspace.
     *
     */
    File getWorkspaceRootDirectory();

    /**
     * True if this is the root Bazel Package that contains the WORKSPACE file.
     */
    boolean isWorkspaceRoot();
}
