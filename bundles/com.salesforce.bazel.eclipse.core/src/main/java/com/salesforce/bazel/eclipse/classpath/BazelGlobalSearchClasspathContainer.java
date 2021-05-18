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
import org.eclipse.jface.preference.IPreferenceStore;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.preferences.BazelPreferenceKeys;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.index.BazelJvmIndexClasspath;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmClasspathResponse;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
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
 */
public class BazelGlobalSearchClasspathContainer extends BaseBazelClasspathContainer {
    public static final String CONTAINER_NAME = "com.salesforce.bazel.eclipse.BAZEL_GLOBAL_SEARCH_CONTAINER";

    protected final BazelConfigurationManager config;
    protected final BazelJvmIndexClasspath bazelJvmIndexClasspath;

    private static List<BazelJvmIndexClasspath> instances = new ArrayList<>();

    public BazelGlobalSearchClasspathContainer(IProject eclipseProject) throws IOException, InterruptedException,
            BackingStoreException, JavaModelException, BazelCommandLineToolConfigurationException {
        this(eclipseProject, BazelPluginActivator.getResourceHelper());
    }

    public BazelGlobalSearchClasspathContainer(IProject eclipseProject, ResourceHelper resourceHelper)
            throws IOException, InterruptedException, BackingStoreException, JavaModelException,
            BazelCommandLineToolConfigurationException {
        super(eclipseProject, resourceHelper);

        BazelPluginActivator activator = BazelPluginActivator.getInstance();
        config = activator.getConfigurationManager();

        OperatingEnvironmentDetectionStrategy os = activator.getOperatingEnvironmentDetectionStrategy();
        BazelExternalJarRuleManager externalJarManager = activator.getBazelExternalJarRuleManager();

        // check if the user has provided an additional location to look for jars
        List<File> additionalJarLocations = null;
        IPreferenceStore prefs = activator.getPreferenceStore();
        String jarCacheDir = prefs.getString(BazelPreferenceKeys.EXTERNAL_JAR_CACHE_PATH_PREF_NAME);
        if (jarCacheDir != null) {
            // user has specified a location, make sure it exists
            File jarCacheDirFile = new File(jarCacheDir);
            if (jarCacheDirFile.exists()) {
                additionalJarLocations = new ArrayList<>();
                additionalJarLocations.add(jarCacheDirFile);
            }
        }

        bazelJvmIndexClasspath =
                new BazelJvmIndexClasspath(this.bazelWorkspace, os, externalJarManager, additionalJarLocations);
        instances.add(bazelJvmIndexClasspath);
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
    protected BazelJvmClasspathResponse computeClasspath() {
        if (!config.isGlobalClasspathSearchEnabled()) {
            // user has disabled the global search feature
            return new BazelJvmClasspathResponse();
        }

        // the Java SDK will produce a list of logical classpath entries
        long startTime = System.currentTimeMillis();
        BazelJvmClasspathResponse computedClasspath =
                bazelJvmIndexClasspath.getClasspathEntries(new EclipseWorkProgressMonitor(null));
        long endTime = System.currentTimeMillis();

        BazelPluginActivator.info(
            "BazelGlobalSearchClasspathContainer completed indexing in " + (endTime - startTime) + " milliseconds.");

        return computedClasspath;
    }

    // TODO this clean() method should not be static
    public static void clean() {
        for (BazelJvmIndexClasspath instance : instances) {
            instance.clearCache();
        }
    }
}