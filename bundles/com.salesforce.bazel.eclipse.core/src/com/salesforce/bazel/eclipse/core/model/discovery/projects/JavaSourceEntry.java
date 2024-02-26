package com.salesforce.bazel.eclipse.core.model.discovery.projects;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.core.runtime.IPath;

/**
 * A source entry points to exactly one <code>.java</code> source file. It contains additional logic for extracting the
 * package path from the location.
 */
public class JavaSourceEntry implements Entry {

    static boolean endsWith(IPath path, IPath lastSegments) {
        if (path.segmentCount() < lastSegments.segmentCount()) {
            return false;
        }

        lastSegments = lastSegments.makeRelative().removeTrailingSeparator();

        while (!lastSegments.isEmpty()) {
            if (!path.lastSegment().equals(lastSegments.lastSegment())) {
                return false;
            }

            path = path.removeLastSegments(1);
            lastSegments = lastSegments.removeLastSegments(1);
        }

        // all last segments match at this point
        return true;
    }

    private final IPath relativePath;
    private final IPath relativePathParent;
    private final IPath bazelPackageLocation;

    IPath detectedPackagePath;

    public JavaSourceEntry(IPath relativePath, IPath bazelPackageLocation) {
        this.relativePath = relativePath;
        this.bazelPackageLocation = bazelPackageLocation;
        relativePathParent = relativePath.removeLastSegments(1).removeTrailingSeparator();
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
        var other = (JavaSourceEntry) obj;
        return Objects.equals(bazelPackageLocation, other.bazelPackageLocation)
                && Objects.equals(relativePath, other.relativePath);
    }

    /**
     * @return the Bazel package location this entry is contained in
     */
    public IPath getBazelPackageLocation() {
        return bazelPackageLocation;
    }

    /**
     * {@return absolute location of of the container of this path entry}
     */
    public IPath getContainingFolderPath() {
        return bazelPackageLocation;
    }

    public IPath getDetectedPackagePath() {
        return requireNonNull(detectedPackagePath, () -> "no package path detected for: " + getLocation());
    }

    /**
     * {@return the absolute path in the local file system, i.e. container path plus the relative path}
     */
    public IPath getLocation() {
        return bazelPackageLocation.append(relativePath);
    }

    /**
     * {@return the relative path within the container}
     */
    public IPath getPath() {
        return relativePath;
    }

    /**
     * {@return the parent folder path of <code>#getPath()</code>}
     */
    public IPath getPathParent() {
        return relativePathParent;
    }

    /**
     * @return first few segments of {@link #getPathParent()} (relative to {@link #getBazelPackageLocation()}) which
     *         could be the source directory, or <code>null</code> if unlikely
     */
    public IPath getPotentialSourceDirectoryRoot() {
        var detectedPackagePath = getDetectedPackagePath();

        // note, we check the full path because we *want* to identify files from targets defined within a Java package
        if (endsWith(bazelPackageLocation.append(relativePathParent), detectedPackagePath)) {
            // this is safe call even when relativePathParent has less segments then detectedPackagePath
            return relativePathParent.removeLastSegments(detectedPackagePath.segmentCount());
        }

        return null; // not following Java package structure conventions
    }

    public boolean hasDetectedPackagePath() {
        return detectedPackagePath != null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bazelPackageLocation, relativePath);
    }

    /**
     * {@return <code>true</code> if the entry is generated, i.e. located outside a Bazel package, <code>false</code>
     * otherwise}
     */
    public boolean isExternalOrGenerated() {
        return false;
    }

    @Override
    public String toString() {
        return relativePath + " (relativePathParent=" + relativePathParent + ", bazelPackageLocation="
                + bazelPackageLocation + ", detectedPackagePath=" + detectedPackagePath + ")";
    }
}