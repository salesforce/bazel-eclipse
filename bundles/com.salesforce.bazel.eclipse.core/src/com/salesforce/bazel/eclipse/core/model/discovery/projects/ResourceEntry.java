package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import java.util.Objects;

import org.eclipse.core.runtime.IPath;

/**
 * A single file entry pointing to a single resource file.
 */
public class ResourceEntry implements Entry {

    private final IPath relativePath;
    private final IPath resourceStripPrefix;

    IPath detectedRootPath;

    public ResourceEntry(IPath relativePath, IPath resourceStripPrefix) {
        this.relativePath = relativePath;
        this.resourceStripPrefix = resourceStripPrefix;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        var other = (ResourceEntry) obj;
        return Objects.equals(relativePath, other.relativePath);
    }

    public IPath getRelativePath() {
        return relativePath;
    }

    public IPath getResourceStripPrefix() {
        return resourceStripPrefix;
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath);
    }

    @Override
    public String toString() {
        return "Resource[" + relativePath + "]";
    }

}