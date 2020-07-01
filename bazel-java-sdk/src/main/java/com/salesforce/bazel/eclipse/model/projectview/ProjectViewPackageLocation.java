package com.salesforce.bazel.eclipse.model.projectview;

import java.io.File;
import java.util.Objects;

import com.salesforce.bazel.eclipse.model.BazelPackageLocation;

/**
 * Represents a line in a project view file.
 * <p>
 * TODO having this distinct from BazelPackageInfo adds complexity to the import logic. Revisit
 * whether we can merge it.
 *
 */
public class ProjectViewPackageLocation implements BazelPackageLocation {
    
    private final File workspaceRootDirectory;
    private final String packagePath;

    public ProjectViewPackageLocation(File workspaceRootDirectory, String packagePath) {
        this.workspaceRootDirectory = Objects.requireNonNull(workspaceRootDirectory);
        this.packagePath = Objects.requireNonNull(packagePath);
        if (new File(this.packagePath).isAbsolute()) {
            throw new IllegalArgumentException("[" + packagePath + "] must be relative");
        }
    }

    @Override
    public String getBazelPackageNameLastSegment() {
        return new File(this.packagePath).getName();
    }

    @Override
    public String getBazelPackageFSRelativePath() {
        return this.packagePath;
    }

    @Override
    public File getWorkspaceRootDirectory() {
        return this.workspaceRootDirectory;
    }

    @Override
    public boolean isWorkspaceRoot() {
        return this.packagePath.isEmpty();
    }
    
    @Override
    public String getBazelPackageName() {
        if ("".equals(packagePath)) {
            // the caller is referring to the WORKSPACE root, which for build operations can
            // (but not always) means that the user wants to build the entire workspace.

            // TODO refine this, so that if the root directory contains a BUILD file with a Java package to 
            // somehow handle that workspace differently 
            // Docs should indicate that a better practice is to keep the root dir free of an actual package
            // For now, assume that anything referring to the root dir is a proxy for 'whole repo'
            return "//...";
        }
        return "//"+packagePath;
    }
    
    @Override
    public int hashCode() {
        return this.workspaceRootDirectory.hashCode() ^ this.packagePath.hashCode();
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof ProjectViewPackageLocation) {
            ProjectViewPackageLocation o = (ProjectViewPackageLocation)other;
            return this.workspaceRootDirectory.equals(o.workspaceRootDirectory) && this.packagePath.equals(o.packagePath);
        }
        return false;
    }
    
    @Override
    public String toString() {
        return "package path: " + this.packagePath;
    }

}
