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
 *
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.eclipse.jdtls.config;

import java.io.File;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.jdtls.managers.BazelBuildSupport;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterFactory;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;

/**
 * A factory class to create Eclipse projects from packages in a Bazel workspace.
 * <p>
 * TODO add test coverage.
 */
public final class BazelEclipseProjectFactory {

    // TODO do an analysis of the workspace to determine the correct JDK to bind
    // into the bazel project
    public static final String STANDARD_VM_CONTAINER_PREFIX = "org.eclipse.jdt.launching.JRE_CONTAINER";

    /**
     * After import, the activated target is a single line, like: bazel.activated.target0=//projects/libs/foo:*
     * ($SLASH_OK bazel path) which activates all targets by use of the wildcard. But users may wish to activate a
     * subset of the targets for builds, in which the prefs lines will look like:
     * bazel.activated.target0=//projects/libs/foo:barlib bazel.activated.target1=//projects/libs/foo:bazlib
     */
    public static final String TARGET_PROPERTY_PREFIX = "bazel.activated.target";

    private static final LogHelper LOG = LogHelper.log(BazelEclipseProjectFactory.class);

    /**
     * Imports a workspace. This version does not yet allow the user to be selective - it imports all Java packages that
     * it finds in the workspace.
     *
     * @return the list of Eclipse IProject objects created during import; by contract the first element in the list is
     *         the IProject object created for the 'bazel workspace' project node which is a special container project
     */
    public static void importWorkspace(BazelPackageLocation workspaceRootPackage,
            List<BazelPackageLocation> selectedBazelPackages, WorkProgressMonitor progressMonitor,
            IProgressMonitor monitor) {
        var bazelWorkspaceRoot = workspaceRootPackage.getWorkspaceRootDirectory().getAbsolutePath();
        var bazelWorkspaceRootDirectory = new File(bazelWorkspaceRoot);

        LOG.debug("Start import process for root directory {}", bazelWorkspaceRootDirectory.getPath());

        var subMonitor = SubMonitor.convert(monitor, selectedBazelPackages.size());
        subMonitor.setTaskName("Getting the Aspect Information for targets");
        subMonitor.split(1);

        // Many collaborators need the Bazel workspace directory location, so we stash
        // it in an accessible global location
        // currently we only support one Bazel workspace in an Eclipse workspace

        EclipseBazelWorkspaceContext.getInstance().setBazelWorkspaceRootDirectory(
            BazelWorkspaceScanner.getBazelWorkspaceName(bazelWorkspaceRoot), bazelWorkspaceRootDirectory);

        BazelBuildSupport.calculateExcludedFilePatterns(bazelWorkspaceRootDirectory.getAbsolutePath());

        var projectImporterFactory = new ProjectImporterFactory(workspaceRootPackage, selectedBazelPackages);
        final var projectImporter = projectImporterFactory.build();

        projectImporter.run(subMonitor);

        subMonitor.done();

        LOG.debug("Finish import process for root directory {}", bazelWorkspaceRootDirectory.getPath());
    }

    private BazelEclipseProjectFactory() {

    }
}
