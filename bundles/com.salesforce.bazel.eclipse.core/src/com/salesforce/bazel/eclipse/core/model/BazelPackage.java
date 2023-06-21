package com.salesforce.bazel.eclipse.core.model;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_BUILD;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.FILE_NAME_BUILD_BAZEL;
import static java.lang.String.format;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * This class represents a Bazel package.
 * <p>
 * A Bazel package is a directory with a <code>BUILD</code> or <code>BUILD.bazel</code> file.
 * </p>
 * <p>
 * See <a href="https://bazel.build/concepts/build-ref">Workspaces, packages, and targets</a> in the Bazel documentation
 * for further details.
 * </p>
 */
public final class BazelPackage extends BazelElement<BazelPackageInfo, BazelWorkspace> {

    /**
     * Utility function to find a <code>BUILD.bazel</code> or <code>BUILD</code> file in a directory.
     * <p>
     * This method is used by {@link BazelPackage#exists()} to determine whether a workspace exists.
     * </p>
     *
     * @param path
     *            the path to check
     * @return the found workspace file (maybe <code>null</code> if none exist)
     * @see #isBuildFileName(String)
     */
    public static Path findBuildFile(Path path) {
        for (String packageFile : List.of(FILE_NAME_BUILD_BAZEL, FILE_NAME_BUILD)) {
            var packageFilePath = path.resolve(packageFile);
            if (isRegularFile(packageFilePath)) {
                return packageFilePath;
            }
        }
        return null;
    }

    /**
     * Utility function to check whether a given file name is <code>BUILD.bazel</code> or <code>BUILD</code>.
     *
     * @param fileName
     *            the file name to check
     * @return <code>true</code> if the file name is either <code>BUILD.bazel</code> or <code>BUILD</code>,
     *         <code>false</code> otherwise
     * @see #findBuildFile(Path)
     */
    public static boolean isBuildFileName(String fileName) {
        for (String packageFile : List.of(FILE_NAME_BUILD_BAZEL, FILE_NAME_BUILD)) {
            if (packageFile.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private final BazelWorkspace parent;
    private final BazelLabel label;

    private final IPath packagePath;

    BazelPackage(BazelWorkspace parent, IPath packagePath) throws NullPointerException, IllegalArgumentException {
        this.packagePath =
                requireNonNull(packagePath, "No package path specified").makeRelative().removeTrailingSeparator();
        this.parent = requireNonNull(parent, "No workspace provided!");
        label = new BazelLabel("//" + this.packagePath.toString());
    }

    @Override
    protected BazelPackageInfo createInfo() throws CoreException {
        var buildFile = findBuildFile();
        if (buildFile == null) {
            throw new CoreException(
                    Status.error(format("Package '%s' does not exist in workspace '%s'!", label, parent.getName())));
        }

        var targets = BazelPackageInfo.queryForTargets(this, getModelManager().getExecutionService());
        return new BazelPackageInfo(buildFile, this, targets);
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
        var other = (BazelPackage) obj;
        return Objects.equals(parent, other.parent) && Objects.equals(packagePath, other.packagePath);
    }

    /**
     * Indicates if the {@link #getLocation() location} points to a directory with one of the supported Bazel BUILD
     * files.
     *
     * @return <code>true</code> if the package exists in the file system.
     */
    @Override
    public boolean exists() {
        var path = packagePath();
        return isDirectory(path) && (findBuildFile(path) != null);
    }

    /**
     * Searches the packages directory for a build file and returns it.
     * <p>
     * Note: this method should not be used regularly. It's a helper useful during <i>loading</i> of the package. The
     * preferred method is {@link #getBuildFile()}, which will return the file found when the package was loaded.
     * </p>
     *
     * @return the found build file (maybe <code>null</code>)
     */
    Path findBuildFile() {
        return findBuildFile(packagePath());
    }

    /**
     * @return the {@link BazelBuildFile build file for this package}
     * @throws CoreException
     */
    public BazelBuildFile getBazelBuildFile() throws CoreException {
        return new BazelBuildFile(this, getBuildFileLocation());
    }

    /**
     * The {@link BazelProject Bazel project} for this package
     * <p>
     * This method performs a search in the Eclipse workspace for a matching project representing this package. The
     * returned project typically represents multiple targets.
     * </p>
     *
     * @return the Bazel package project
     * @throws CoreException
     *             if the project cannot be found in the Eclipse workspace
     */
    public BazelProject getBazelProject() throws CoreException {
        return getInfo().getBazelProject();
    }

    /**
     * Returns a Bazel target for the given name.
     * <p>
     * This is a handle-only method. The underlying target may or may not exist in the package.
     * </p>
     *
     * @param targetName
     *            name of the target (must not be <code>null</code>)
     * @return the {@link BazelTarget Bazel target handle} (never <code>null</code>)
     */
    public BazelTarget getBazelTarget(String targetName) {
        return new BazelTarget(this, targetName);
    }

    /**
     * Returns the list of {@link BazelTarget targets} of this package.
     * <p>
     * This method may open/load the package and interacts with Bazel to obtain the list.
     * </p>
     *
     * @return the list of targets contained in this package
     * @throws CoreException
     *             if there was a problem obtaining the list
     */
    public List<BazelTarget> getBazelTargets() throws CoreException {
        return getInfo().getTargets().stream().map(this::getBazelTarget).collect(toList());
    }

    @Override
    public BazelWorkspace getBazelWorkspace() {
        return parent;
    }

    /**
     * {@return the absolute path to the BUILD file used by this package}
     *
     * @throws CoreException
     *             if the package does not exist
     */
    public Path getBuildFile() throws CoreException {
        return getInfo().getBuildFile();
    }

    /**
     * {@return the absolute location to the BUILD file used by this package}
     *
     * @throws CoreException
     *             if the package does not exist
     */
    public IPath getBuildFileLocation() throws CoreException {
        return getLocation().append(getInfo().getBuildFile().getFileName().toString());
    }

    @Override
    public BazelLabel getLabel() {
        return label;
    }

    @Override
    public IPath getLocation() {
        return getBazelWorkspace().getLocation().append(getWorkspaceRelativePath());
    }

    BazelModelManager getModelManager() {
        return parent.getModelManager();
    }

    @Override
    public BazelWorkspace getParent() {
        return parent;
    }

    /**
     * Returns the parent Bazel package of this Bazel package. Returns <code>null</code> if this is the root package,
     * i.e. {@link #isRoot()} returns <code>true</code>.
     * <p>
     * This is a handle-only method. The underlying resource may or may not exist.
     * </p>
     *
     * @return the {@link BazelPackage Bazel package handle} of <code>null</code> (in case {@link #isRoot()} returns
     *         <code>true</code>)
     */
    public BazelPackage getParentPackage() {
        if (isRoot()) {
            return null; // we intentionally return null to prevent from accidental endless loops
        }

        return getBazelWorkspace().getBazelPackage(packagePath.removeLastSegments(1));
    }

    /**
     * Returns a relative path of this package with respect to its workspace. Returns the empty path for the
     * {@link BazelWorkspace Bazel workspace} and {@link BazelModel model} itself.
     * <p>
     * The root package is represented as the empty path.
     * </p>
     * <p>
     * This is a handle operation; the element does not need to exist. If this package does exist, its path can be
     * safely assumed to be valid.
     * </p>
     * <p>
     * A workspace-relative path indicates the route from the workspace to the element. Within a workspace, there is
     * exactly one such path for any given Bazel element. The returned path never has a trailing slash.
     * </p>
     * <p>
     * Workspace-relative paths are recommended over absolute paths, since the former are not affected if the workspace
     * is renamed/relocated/moved.
     * </p>
     * <p>
     * By definition, a workspace relative path is never absolute and never has a trailing slash, i.e.
     * {@link IPath#isAbsolute()} returns <code>false</code> and {@link IPath#hasTrailingSeparator()} returns
     * <code>false</code>.
     * </p>
     *
     * @return the relative path of this element with respect to its workspace
     * @see #getLocation()
     * @see #getBazelWorkspace()
     * @see Path#EMPTY
     */
    public IPath getWorkspaceRelativePath() {
        return packagePath;
    }

    public boolean hasBazelProject() throws CoreException {
        return getInfo().findProject() != null;
    }

    public boolean hasBazelTarget(String name) throws CoreException {
        return getInfo().getTargets().contains(name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, packagePath);
    }

    /**
     * Indicates if this is the root package (<code>//</code>).
     *
     * @return <code>true</code> if this is the root package, <code>false</code> otherwise
     */
    public boolean isRoot() {
        return packagePath.isEmpty();
    }

    private java.nio.file.Path packagePath() {
        return getLocation().toPath();
    }

}
