package com.salesforce.bazel.eclipse.model.projectview;

import java.io.File;
import java.util.Objects;

import com.salesforce.bazel.eclipse.model.BazelPackageLocation;

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
