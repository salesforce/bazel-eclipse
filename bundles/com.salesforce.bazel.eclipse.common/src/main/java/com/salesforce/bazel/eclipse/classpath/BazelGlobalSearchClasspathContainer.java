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
package com.salesforce.bazel.eclipse.classpath;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.preferences.BazelPreferenceKeys;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.index.jvm.BazelJvmIndexClasspath;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmClasspathResponse;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * The global search feature enables the user to search for types (e.g. Java classes) using the Open Type dialog, and
 * find results that are in the Bazel Workspace even if none of the currently imported packages has that type in the
 * transitive closure of dependencies.
 * <p>
 * Internally, this means we take the union of all dependencies (currently, jar and source files) found in the Bazel
 * Workspace, plus all of the types built within the workspace, and adding them to this synthetic classpath container
 * attached to the 'Bazel Workspace' project in Eclipse. If the Bazel Workspace has an enormous set of dependencies,
 * this may make type search slow so we allow the user to disable it in the Bazel preferences.
 * <p>
 * This needs to be reconfigurable, if the user deletes the Bazel Workspace from the Eclipse Workspace, and loads
 * another.
 */
public class BazelGlobalSearchClasspathContainer extends BaseBazelClasspathContainer {
    private static final LogHelper LOG = LogHelper.log(BazelGlobalSearchClasspathContainer.class);

    public static final String CONTAINER_NAME = "com.salesforce.bazel.eclipse.BAZEL_GLOBAL_SEARCH_CONTAINER";

    protected String bazelWorkspaceName = null;
    protected final BazelConfigurationManager config;
    protected BazelJvmIndexClasspath bazelJvmIndexClasspath;

    private static List<BazelJvmIndexClasspath> instances = new ArrayList<>();

    public BazelGlobalSearchClasspathContainer(IProject eclipseProject) throws IOException, InterruptedException,
            BackingStoreException, JavaModelException, BazelCommandLineToolConfigurationException {
        this(eclipseProject, ComponentContext.getInstance().getResourceHelper(),
                ComponentContext.getInstance().getJavaCoreHelper(), ComponentContext.getInstance().getProjectManager(),
                ComponentContext.getInstance().getOsStrategy(), ComponentContext.getInstance().getBazelWorkspace());
    }

    public BazelGlobalSearchClasspathContainer(IProject eclipseProject, ResourceHelper resourceHelper,
            JavaCoreHelper jcHelper, BazelProjectManager bpManager,
            OperatingEnvironmentDetectionStrategy osDetectStrategy, BazelWorkspace bazelWorkspace)
            throws IOException, InterruptedException, BackingStoreException, JavaModelException,
            BazelCommandLineToolConfigurationException {
        super(eclipseProject, resourceHelper, jcHelper, bpManager, osDetectStrategy, bazelWorkspace);
        config = ComponentContext.getInstance().getConfigurationManager();
    }

    @Override
    public String getDescription() {
        return "Bazel Global Search Classpath";
    }

    @Override
    public IClasspathEntry[] getClasspathEntries() {
        // this method is overridden just for a breakpoint opportunity
        return super.getClasspathEntries();
    }

    @Override
    public BazelJvmClasspathResponse computeClasspath() {
        BazelWorkspace bazelWorkspace = ComponentContext.getInstance().getBazelWorkspace();
        if (!config.isGlobalClasspathSearchEnabled() || (bazelWorkspace == null)) {
            // user has disabled the global search feature, or hasnt imported a bazel workspace yet
            return new BazelJvmClasspathResponse();
        }

        if (!bazelWorkspace.getName().equals(bazelWorkspaceName)) {
            // this is the first time with this Bazel Workspace, load our collaborators
            OperatingEnvironmentDetectionStrategy os = ComponentContext.getInstance().getOsStrategy();
            BazelExternalJarRuleManager externalJarManager = ComponentContext.getInstance().getBazelExternalJarRuleManager();

            // check if the user has provided an additional location to look for jars
            List<File> additionalJarLocations = loadAdditionalLocations();
            bazelJvmIndexClasspath =
                    new BazelJvmIndexClasspath(bazelWorkspace, os, externalJarManager, additionalJarLocations);
            instances.add(bazelJvmIndexClasspath);
        }

        // the Java SDK will produce a list of logical classpath entries
        long startTime = System.currentTimeMillis();
        EclipseWorkProgressMonitor monitor = new EclipseWorkProgressMonitor(null);
        BazelJvmClasspathResponse computedClasspath = bazelJvmIndexClasspath.getClasspathEntries(monitor);
        long endTime = System.currentTimeMillis();

        LOG.info("completed indexing in [{}] milliseconds.", (endTime - startTime));

        return computedClasspath;
    }

    // TODO this clean() method should not be static
    public static void clean() {
        for (BazelJvmIndexClasspath instance : instances) {
            instance.clearCache();
        }
    }

    /**
     * Returns a copy of the underlying index classpath. This is provided so the caller may do inquiries into the data.
     * Callers are not expected to modify this index.
     */
    public BazelJvmIndexClasspath getIndexClasspath() {
        return bazelJvmIndexClasspath;
    }

    /**
     * Uses the preference store in Eclipse to get additional locations in which to look for jars.
     */
    public static List<File> loadAdditionalLocations() {
        List<File> additionalJarLocations = null;

        String jarCacheDir = ComponentContext.getInstance().getPreferenceStoreHelper()
                .getString(BazelPreferenceKeys.EXTERNAL_JAR_CACHE_PATH_PREF_NAME);
        if (jarCacheDir != null) {
            // user has specified a location, make sure it exists
            File jarCacheDirFile = new File(jarCacheDir);
            if (jarCacheDirFile.exists()) {
                additionalJarLocations = new ArrayList<>();
                additionalJarLocations.add(jarCacheDirFile);
            }
        }
        return additionalJarLocations;
    }
}