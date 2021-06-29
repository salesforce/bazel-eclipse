package com.salesforce.bazel.sdk.graph;

import java.io.File;
import java.util.List;

import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * In memory package location.
 * <p>
 * TODO we need to rethink BazelPackageLocation abstraction.
 *
 */
public class InMemoryPackageLocation implements BazelPackageLocation {
    private String path;
    private String lastSegment;

    // root node
    public InMemoryPackageLocation() {
        this.path = null;
        this.lastSegment = "";
    }

    public InMemoryPackageLocation(String path) {
        if (path.startsWith("//")) {
            this.path = path.substring(2);
        } else {
            this.path = path;
        }
        this.lastSegment = "";
        int lastSlash = path.indexOf(File.separator);
        if (lastSlash > 0) {
            this.lastSegment = path.substring(lastSlash + 1);
        }
    }

    @Override
    public String getBazelPackageNameLastSegment() {
        return lastSegment;
    }

    @Override
    public String getBazelPackageFSRelativePath() {
        return path;
    }

    @Override
    public File getWorkspaceRootDirectory() {
        return null;
    }

    @Override
    public boolean isWorkspaceRoot() {
        return path == null;
    }

    @Override
    public String getBazelPackageName() {
        return path;
    }

    @Override
    public List<BazelPackageLocation> gatherChildren() {
        // TODO hard to implement, we plan to rework this class
        return null;
    }

    @Override
    public List<BazelPackageLocation> gatherChildren(String pathFilter) {
        // TODO hard to implement, we plan to rework this class
        return null;
    }
}
