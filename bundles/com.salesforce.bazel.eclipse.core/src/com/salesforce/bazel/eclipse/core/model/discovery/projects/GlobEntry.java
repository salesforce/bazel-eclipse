package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.IPath;

/**
 * A glob is used to denote directory with files and potentially excludes.
 */
public class GlobEntry implements Entry {

    private final IPath relativeDirectoryPath;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;

    public GlobEntry(IPath relativeDirectoryPath, List<String> includePatterns, List<String> excludePatterns) {
        this.relativeDirectoryPath = relativeDirectoryPath;
        this.includePatterns = includePatterns;
        this.excludePatterns = excludePatterns;
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
        var other = (GlobEntry) obj;
        return Objects.equals(excludePatterns, other.excludePatterns)
                && Objects.equals(includePatterns, other.includePatterns)
                && Objects.equals(relativeDirectoryPath, other.relativeDirectoryPath);
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public List<String> getIncludePatterns() {
        return includePatterns;
    }

    public IPath getRelativeDirectoryPath() {
        return relativeDirectoryPath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(excludePatterns, includePatterns, relativeDirectoryPath);
    }

    @Override
    public String toString() {
        if (excludePatterns == null) {
            return relativeDirectoryPath + "/" + includePatterns;
        }
        return relativeDirectoryPath + "/" + includePatterns + " (excluding " + excludePatterns + ")";
    }
}