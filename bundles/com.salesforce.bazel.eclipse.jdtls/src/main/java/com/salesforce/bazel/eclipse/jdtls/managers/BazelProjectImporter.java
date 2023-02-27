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
package com.salesforce.bazel.eclipse.jdtls.managers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.ls.core.internal.AbstractProjectImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.jdtls.BazelJdtLsPlugin;
import com.salesforce.bazel.eclipse.jdtls.config.BazelEclipseProjectFactory;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;

@SuppressWarnings("restriction")
public final class BazelProjectImporter extends AbstractProjectImporter {
    private static Logger LOG = LoggerFactory.getLogger(BazelProjectImporter.class);

    private static String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
        // Since the actual preferences come from the VSCode via LSP, it is not defined
        // when we get them. But this is the place where we need them for sure,
        // this is why we obtain them here. Right now the preferences have already
        // come from VSCode.
        // It will still work well for others UI clients.
        final var preferencesManager = preparePreferences();

        if (!checkIsBazelImportEnabled(preferencesManager) || !checkRootFolder()) {
            return false;
        }

        // See MavenProjectImporter for details why this side-effect is here
        directories = Arrays.asList(Path.of(rootFolder.getPath()));

        return true;

    }

    private boolean checkIsBazelImportEnabled(final B2EPreferncesManager preferencesManager) {
        return (preferencesManager != null) && preferencesManager.isImportBazelEnabled();
    }

    private boolean checkRootFolder() {
        return (rootFolder != null) && rootFolder.exists() && rootFolder.isDirectory()
                && (new File(rootFolder, BazelBuildSupport.WORKSPACE_FILE_NAME).exists() || new File(rootFolder,
                        BazelBuildSupport.WORKSPACE_FILE_NAME + BazelBuildSupport.BAZEL_FILE_NAME_SUFIX).exists());
    }

    private String getBazelPackageRelativePath(BazelPackageLocation bpi) {
        return SystemUtils.IS_OS_WINDOWS ? //
                bpi.getBazelPackageFSRelativePath().replace('\\', '/') : //
                bpi.getBazelPackageFSRelativePath();
    }

    @Override
    public void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException {
        try {
            // TODO the SDK now has pluggable lang support (alas, java is the only option now)
            // which means at some point you need to initialize the Java features of the SDK
            var workspaceScanner = new BazelWorkspaceScanner();
            BazelPackageLocation workspaceRootPackage = workspaceScanner.getPackages(rootFolder.getAbsolutePath());

            if (workspaceRootPackage == null) {
                throw new IllegalArgumentException();
            }

            List<BazelPackageLocation> allBazelPackages = new ArrayList<>(workspaceRootPackage.gatherChildren());

            var bazelPackagesToImport = allBazelPackages;

            var targetsFile = new File(rootFolder, BazelBuildSupport.BAZELPROJECT_FILE_NAME_SUFIX);

            if (targetsFile.exists()) {
                var projectView = new ProjectView(rootFolder, readFile(targetsFile.getPath()));

                Set<String> projectViewPaths = projectView.getDirectories().stream()
                        .map(BazelPackageLocation::getBazelPackageFSRelativePath).collect(Collectors.toSet());

                bazelPackagesToImport = allBazelPackages.stream()
                        .filter(bpi -> projectViewPaths.contains(getBazelPackageRelativePath(bpi)))
                        .collect(Collectors.toList());
            }

            var progressMonitor = WorkProgressMonitor.NOOP;

            BazelEclipseProjectFactory.importWorkspace(workspaceRootPackage, bazelPackagesToImport, progressMonitor,
                monitor);
        } catch (IOException e) {
            LOG.error("Import into workspace failed", e);
        }
    }

    private B2EPreferncesManager preparePreferences() {
        final var jdtlsPrefs = BazelJdtLsPlugin.getDefault().getJdtLsPreferences();
        final var preferencesManager = B2EPreferncesManager.getInstance();

        preferencesManager.setConfiguration(jdtlsPrefs);

        return preferencesManager;
    }

    @Override
    public void reset() {

    }
}
