/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.eclipse.project;

import java.io.File;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Path;

import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Knows how to link files (used only during project import).
 */
public class EclipseFileLinker {
    private static final LogHelper LOG = LogHelper.log(EclipseFileLinker.class);

    private final File bazelWorkspaceRootDirectory;
    private final ResourceHelper resourceHelper;

    public EclipseFileLinker(File bazelWorkspaceRootDirectory, ResourceHelper resourceHelper) {
        this.bazelWorkspaceRootDirectory = Objects.requireNonNull(bazelWorkspaceRootDirectory);
        this.resourceHelper = Objects.requireNonNull(resourceHelper);
    }

    public boolean link(String packageFSPath, IProject eclipseProject, String fileName) {
        var retval = true;

        var f = new File(new File(bazelWorkspaceRootDirectory, packageFSPath), fileName);
        if (f.exists() && f.isFile()) {
            var projectFile = resourceHelper.getProjectFile(eclipseProject, fileName);
            try {
                resourceHelper.createFileLink(projectFile, Path.fromOSString(f.getCanonicalPath()), IResource.REPLACE,
                    null);
            } catch (Exception anyE) {
                // TODO throwing this exception just writes a log message, we need a modal error popup for this error
                LOG.error("Failure to link file [{}] for project [{}]", FSPathHelper.getCanonicalPathStringSafely(f),
                    eclipseProject.getName());
                throw new IllegalStateException(anyE);
            }
        } else {
            LOG.error("Tried to link a non-existant file [{}] for project [{}]",
                FSPathHelper.getCanonicalPathStringSafely(f), eclipseProject.getName());
            retval = false;
        }
        return retval;
    }
}
