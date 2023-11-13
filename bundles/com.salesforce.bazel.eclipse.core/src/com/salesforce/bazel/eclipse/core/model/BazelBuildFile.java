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

import com.salesforce.bazel.eclipse.core.model.buildfile.MacroCall;
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
 */
public final class BazelBuildFile extends BazelElement<BazelBuildFileInfo, BazelPackage> {

    private final BazelPackage bazelPackage;
    private final IPath buildFileLocation;

    BazelBuildFile(BazelPackage bazelPackage, IPath buildFileLocation) {
        this.bazelPackage = bazelPackage;
        this.buildFileLocation = buildFileLocation; /* this can be a BUILD or a BUILD.bazel file */
    }

    @Override
    protected BazelBuildFileInfo createInfo() throws CoreException {
        var reader = new BazelBuildFileReader(buildFileLocation.toPath());
        try {
            reader.read();
        } catch (IOException e) {
            throw new CoreException(
                    Status.error(format("Unable to read build file '%s': %s", buildFileLocation, e.getMessage()), e));
        }
        return new BazelBuildFileInfo(
                this,
                reader.getLoadStatements(),
                reader.getMacroCalls(),
                reader.getPackageCall());
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
        var other = (BazelBuildFile) obj;
        return Objects.equals(bazelPackage, other.bazelPackage)
                && Objects.equals(buildFileLocation, other.buildFileLocation);
    }

    @Override
    public boolean exists() throws CoreException {
        return isRegularFile(buildFileLocation.toPath());
    }

    @Override
    public BazelWorkspace getBazelWorkspace() {
        return getParent().getBazelWorkspace();
    }

    @Override
    public BazelLabel getLabel() {
        return new BazelLabel(bazelPackage.getWorkspaceRelativePath().toString(), "BUILD.bazel"); // hardcode to BUILD.bazel to there is only one build file info in the model cache
    }

    @Override
    public IPath getLocation() {
        return buildFileLocation;
    }

    @Override
    public BazelPackage getParent() {
        return bazelPackage;
    }

    public List<MacroCall> getTopLevelMacroCalls() throws CoreException {
        return getInfo().getMacroCalls();
    }

    @Override
    public int hashCode() {
        return Objects.hash(bazelPackage, buildFileLocation);
    }

}
