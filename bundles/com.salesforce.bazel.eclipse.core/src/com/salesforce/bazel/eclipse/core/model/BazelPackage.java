package com.salesforce.bazel.eclipse.core.model;

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

    private final BazelWorkspace parent;
    private final BazelLabel label;
    private final IPath packagePath;

    BazelPackage(BazelWorkspace parent, IPath packagePath) throws NullPointerException, IllegalArgumentException {
        this.packagePath = requireNonNull(packagePath, "No package path specified").makeAbsolute().makeRelative()
                .removeTrailingSeparator();
        this.parent = requireNonNull(parent, "No workspace provided!");
        label = new BazelLabel("//" + this.packagePath.toString());
    }

    @Override
    protected BazelPackageInfo createInfo() throws CoreException {
        var buildFile = findBuildFile(packagePath());
        if (buildFile == null) {
            throw new CoreException(
                    Status.error(format("Package '%s' does not exist in workspace '%s'!", label, parent.getName())));
        }

        var info = new BazelPackageInfo(buildFile, getBazelWorkspace().workspacePath(), this);
        info.load(getModelManager().getExecutionService());
        return info;
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

    @Override
    public boolean exists() {
        var path = packagePath();
        return isDirectory(path) && (findBuildFile(path) != null);
    }

    private Path findBuildFile(Path path) {
        for (String packageFile : List.of("BUILD.bazel", "BUILD")) {
            var packageFilePath = path.resolve(packageFile);
            if (isRegularFile(packageFilePath)) {
                return packageFilePath;
            }
        }
        return null;
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
        return getLocation().toFile().toPath();
    }

}
