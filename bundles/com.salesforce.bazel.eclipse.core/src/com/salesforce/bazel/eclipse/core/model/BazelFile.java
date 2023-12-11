/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - initial implementation
*/
package com.salesforce.bazel.eclipse.core.model;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.eclipse.core.model.buildfile.FunctionCall;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * This class represents the <code>BUILD.bazel</code> or <code>BUILD</code> file found inside a {@link BazelPackage}.
 * <p>
 * Although technically not necessary for building Bazel projects when working with <code>bazel query</code> and
 * <code>bazel build</code>, the build file becomes very important for IDEs. Often <code>bazel query</code> tells you
 * how Bazel sees the world. This may be different from how a developers describes it. For example, with
 * <code>bazel query</code> you will see {@link BazelPackage packages} and {@link BazelTarget targets} but not
 * <a href="https://bazel.build/extending/macros">macros</a> and functions such as <code>glob</code>. Therefore this
 * class exist in the Bazel model to allow the IDEs to drill down into the build file and discovers those.
 * </p>
 * <p>
 * See <a href="https://bazel.build/concepts/build-ref">Workspaces, packages, and targets</a> in the Bazel documentation
 * for further details.
 * </p>
 *
 * @param <I>
 *            type of element info
 * @param <P>
 *            type of element parent
 */
public sealed abstract class BazelFile<I extends BazelFileInfo<?>, P extends BazelElement<?, ?>>
        extends BazelElement<I, P> permits BazelBuildFile, BazelModuleFile {

    protected final IPath buildFileLocation;
    private final P bazelParent;
    private final IPath workspaceRelativePath;
    private final IPath packagePath;
    private final IPath packageRelativePath;

    /**
     * @param bazelParent
     *            the parent (owning) element)
     * @param packagePath
     *            the package path (used to obtain the proper {@link #getLabel()})
     * @param buildFileLocation
     *            the buildFileLocation
     */
    BazelFile(P bazelParent, IPath packagePath, IPath buildFileLocation) {
        this.bazelParent = bazelParent;
        this.buildFileLocation = buildFileLocation;
        this.workspaceRelativePath = buildFileLocation.makeRelativeTo(bazelParent.getBazelWorkspace().getLocation());
        this.packagePath = packagePath;
        this.packageRelativePath = buildFileLocation.makeRelativeTo(packagePath);
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
        var other = (BazelFile<?, ?>) obj;
        return Objects.equals(bazelParent, other.bazelParent)
                && Objects.equals(buildFileLocation, other.buildFileLocation);
    }

    @Override
    public boolean exists() throws CoreException {
        return isRegularFile(buildFileLocation.toPath());
    }

    @Override
    public BazelLabel getLabel() {
        return new BazelLabel(packagePath.toString(), packageRelativePath.lastSegment());
    }

    @Override
    public IPath getLocation() {
        return buildFileLocation;
    }

    @Override
    public P getParent() {
        return bazelParent;
    }

    public List<FunctionCall> getTopLevelCalls() throws CoreException {
        return getInfo().getFunctionCalls();
    }

    /**
     * Returns a relative path of a Bazel file with respect to its workspace.
     * <p>
     * This is a handle operation; the element does not need to exist. If this file does exist, its path can be safely
     * assumed to be valid.
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
     */
    public IPath getWorkspaceRelativePath() {
        return workspaceRelativePath;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bazelParent, buildFileLocation);
    }

    protected BazelStarlarkFileReader readBuildFile() throws CoreException {
        var reader = new BazelStarlarkFileReader(buildFileLocation.toPath());
        try {
            reader.read();
        } catch (IOException e) {
            throw new CoreException(
                    Status.error(format("Unable to read file '%s': %s", buildFileLocation, e.getMessage()), e));
        }
        return reader;
    }

}
