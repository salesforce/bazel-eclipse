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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmClasspath;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmClasspathResponse;

public class BazelClasspathContainer extends BaseBazelClasspathContainer {
    public static final String CONTAINER_NAME = "com.salesforce.bazel.eclipse.BAZEL_CONTAINER";

    protected  BazelJvmClasspath bazelClasspath;

    private static List<BazelJvmClasspath> instances = new ArrayList<>();

    public BazelClasspathContainer(IProject eclipseProject) throws IOException, InterruptedException,
            BackingStoreException, JavaModelException, BazelCommandLineToolConfigurationException {
        this(eclipseProject, BazelPluginActivator.getResourceHelper());
    }

    public BazelClasspathContainer(IProject eclipseProject, ResourceHelper resourceHelper)
            throws IOException, InterruptedException, BackingStoreException, JavaModelException,
            BazelCommandLineToolConfigurationException {
        super(eclipseProject, resourceHelper);

        bazelClasspath = new BazelJvmClasspath(this.bazelWorkspace, bazelProjectManager, bazelProject,
            new EclipseImplicitClasspathHelper(), osDetector, BazelPluginActivator.getBazelCommandManager());
        instances.add(bazelClasspath);
    }

    @Override
    public String getDescription() {
        return "Bazel Classpath Container";
    }

    @Override
    public IClasspathEntry[] getClasspathEntries() {
        // this method is overridden just for a breakpoint opportunity
        return super.getClasspathEntries();
    }

    @Override
    protected BazelJvmClasspathResponse computeClasspath() {
        // the Java SDK will produce a list of logical classpath entries
        return bazelClasspath.getClasspathEntries(new EclipseWorkProgressMonitor(null));
    }

    // TODO this clean() method should not be static
    public static void clean() {
        for (BazelJvmClasspath instance : instances) {
            instance.clean();
        }
    }
}
