/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.eclipse.config;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.ui.wizards.datatransfer.ProjectConfigurator;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.project.EclipseProjectUtils;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.util.BazelConstants;
import com.salesforce.bazel.sdk.workspace.BazelPackageFinder;
import com.salesforce.bazel.sdk.workspace.BuildFileSupport;

// initial version copied from m2e MavenProjectConfigurator

// TODO test if any of this used at all. maybe just legacy cruft

public class BazelProjectConfigurator implements ProjectConfigurator {
    static final LogHelper LOG = LogHelper.log(BazelProjectConfigurator.class);

    /**
     * From a given {@link File}, detects which directories can/should be imported as projects into the workspace and
     * configured by this configurator. This first set of directories is then presented to the user as import proposals.
     *
     * <p>
     * This method must be stateless.
     * </p>
     *
     * @param root
     *            the root directory on which to start the discovery
     * @param eclipseMonitor
     *            the progress monitor
     * @return the children (at any depth) that this configurator suggests to import as project
     */
    @Override
    public Set<File> findConfigurableLocations(File root, IProgressMonitor eclipseMonitor) {
        Set<File> buildFileLocations = new TreeSet<>();
        BazelPackageFinder packageFinder = new BazelPackageFinder();
        EclipseWorkProgressMonitor abstractMonitor = new EclipseWorkProgressMonitor(eclipseMonitor);

        packageFinder.findBuildFileLocations(root, abstractMonitor, buildFileLocations, 0);

        return buildFileLocations;
    }

    /**
     * Tells whether this configurator thinks that a given {@link IContainer} should be also imported as a project into
     * the workspace.
     *
     * <p>
     * This method must be stateless (ideally static) and cannot rely on any class state.
     * </p>
     *
     * @param container
     *            the container to analyze
     * @param monitor
     *            the progress monitor
     * @return true if the given folder is for sure to be considered as a project
     */
    @Override
    public boolean shouldBeAnEclipseProject(IContainer container, IProgressMonitor monitor) {
        IFile buildFile = null;
        for (String buildFileName : BazelConstants.BUILD_FILE_NAMES) {
            buildFile = container.getFile(new Path(buildFileName));
            if (buildFile.exists()) {
                break;
            }
        }
        if ((buildFile == null) || !buildFile.exists()) {
            return false;
        }

        boolean hasRegisteredRule = false;
        try (InputStream is = buildFile.getContents()) {
            hasRegisteredRule = BuildFileSupport.hasRegisteredRules(is);
        } catch (Exception anyE) {
            LOG.error(anyE.getMessage(), anyE);
        }

        return hasRegisteredRule;
    }

    /**
     * Checks whether this configurator can contribute to the configuration of the given project.
     *
     * <p>
     * This method must be stateless.
     * </p>
     *
     * @param project
     *            the project to check for potential configuration
     * @param ignoredPaths
     *            paths that have to be ignored when checking whether this configurator applies. Those will typically be
     *            nested projects (handled separately), or output directories (bin, target, ...).
     * @param monitor
     *            the progress monitor
     * @return <code>true</code> iff this configurator can configure the given project
     */
    @Override
    public boolean canConfigure(IProject project, Set<IPath> ignoredPaths, IProgressMonitor monitor) {
        return shouldBeAnEclipseProject(project, monitor);
    }

    /**
     * Configures a project. This method will only be called if {@link #canConfigure(IProject, Set, IProgressMonitor)}
     * returned <code>true</code> for the given project.
     *
     * <p>
     * This method must be stateless.
     * </p>
     *
     * @param project
     *            the project to configure
     * @param ignoredPaths
     *            paths that have to be ignored when configuring the project. Those will typically be nested projects,
     *            output directories (bin, target, ...)
     * @param monitor
     *            the progress monitor
     */
    @Override
    public void configure(IProject project, Set<IPath> ignoredPaths, IProgressMonitor monitor) {
        try {
            // TODO when will this be called? we add the nature already when we created the project
            EclipseProjectUtils.addNatureToEclipseProject(project, BazelNature.BAZEL_NATURE_ID);
        } catch (CoreException coreEx) {
            LOG.error("Exception adding Bazel nature: {}", coreEx.getMessage());
        }
    }

    // IGNORE DIRECTORIES

    /**
     * Returns the folders to exclude from the analysis that happens on an {@link IProject}.
     *
     * <p>
     * This method must be stateless.
     * </p>
     *
     * @param project
     *            the project to check for content to ignore
     * @param monitor
     *            the progress monitor
     * @return the set of child folders to ignore in import operation. Typically output directories such as bin/ or
     *         target/.
     */
    @Override
    public Set<IFolder> getFoldersToIgnore(IProject project, IProgressMonitor monitor) {
        Set<IFolder> res = new HashSet<IFolder>();

        // do we want to exclude any folders in a package from scanning for BUILD files?
        // TODO .bazelignore

        return res;
    }

    /**
     * Removes from the set of directories those that should not be proposed to the user for import. Those are typically
     * dirty volatile directories such as build output directories.
     *
     * <p>
     * This method must be stateless.
     * </p>
     *
     * @param proposals
     *            the existing import proposals (key is file and value is the list of configurators that have identified
     *            the key as a location they can configure for import). Those can be modified and current method is
     *            expected to remove some entries from this map.
     */
    @Override
    public void removeDirtyDirectories(Map<File, List<ProjectConfigurator>> proposals) {
        // this is not an issue with Bazel
        // there are output directories in the top level workspace root, but they will not have BUILD files in them
    }

}
