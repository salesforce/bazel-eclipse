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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspath;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.JvmUnionClasspath;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy.JvmClasspathAspectStrategy;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy.JvmClasspathStrategy;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.JvmSourceDerivedClasspath;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

public class BazelClasspathContainer extends BaseBazelClasspathContainer {
    private static final LogHelper LOG = LogHelper.log(BazelClasspathContainer.class);

    protected final JvmClasspath bazelClasspath;
    private CallSource lastCallSource = CallSource.UNDEFINED;

    // TODO make this an Eclipse pref
    public boolean USE_DYNAMIC_CP = false;

    private static List<JvmClasspath> instances = new ArrayList<>();

    public BazelClasspathContainer(IProject eclipseProject) throws IOException, InterruptedException,
            BackingStoreException, JavaModelException, BazelCommandLineToolConfigurationException {
        this(eclipseProject, ComponentContext.getInstance().getResourceHelper(), ComponentContext.getInstance().getJavaCoreHelper(),
            ComponentContext.getInstance().getProjectManager(), ComponentContext.getInstance().getOsStrategy(),
                ComponentContext.getInstance().getBazelWorkspace());
    }

    public BazelClasspathContainer(IProject eclipseProject, ResourceHelper resourceHelper, JavaCoreHelper jcHelper,
            BazelProjectManager bpManager, OperatingEnvironmentDetectionStrategy osDetectStrategy,
            BazelWorkspace bazelWorkspace) throws IOException, InterruptedException, BackingStoreException,
            JavaModelException, BazelCommandLineToolConfigurationException {
        super(eclipseProject, resourceHelper, jcHelper, bpManager, osDetectStrategy, bazelWorkspace);

        if (USE_DYNAMIC_CP) {
            // TODO the dynamic classpath will be folded into the strategy concept
            bazelClasspath = new JvmSourceDerivedClasspath(bazelWorkspace, bazelProjectManager, bazelProject,
                    new EclipseImplicitClasspathHelper(), osDetector,
                    ComponentContext.getInstance().getBazelCommandManager(), null);
        } else {
            // TODO this is where we will configure the classpath strategy chain, right now there is just one
            // assemble the list of classpath strategies we support (right now, just one)
            List<JvmClasspathStrategy> strategies = new ArrayList<>();
            strategies.add(new JvmClasspathAspectStrategy(bazelWorkspace, bazelProjectManager, 
                new EclipseImplicitClasspathHelper(), osDetector, ComponentContext.getInstance().getBazelCommandManager()));

            // create the classpath computation engine
            bazelClasspath = new JvmUnionClasspath(bazelWorkspace, bazelProjectManager, bazelProject,
                    new EclipseImplicitClasspathHelper(), osDetector, ComponentContext.getInstance().getBazelCommandManager(),
                    strategies);
        }
        instances.add(bazelClasspath);
    }

    @Override
    public String getDescription() {
        if (USE_DYNAMIC_CP) {
            return "Dynamic Classpath Container";
        }
        return "Bazel Classpath Container";
    }

    @Override
    public IClasspathEntry[] getClasspathEntries() {
        CallSource currentCallSource = getCallSource(Thread.currentThread().getStackTrace());

        if (LOG.isDebugLevel()) {
            LOG.debug("Call source for classpath is {}. Last call source was {}", currentCallSource.name(),
                this.lastCallSource.name());
        }
        if (ObjectUtils.notEqual(currentCallSource, CallSource.UNDEFINED)) {
            lastCallSource = currentCallSource;
        }

        Predicate<IClasspathEntry> isNormalClasspathEntry = classpathEntry -> !(classpathEntry.getPath().toFile()
                .getName().equalsIgnoreCase("Runner_deploy-ijar.jar"));

        // if it is Run/Debug call, then implicit dependencies should be filtered out to prevent the loading of a wrong version of classes
        IClasspathEntry[] classpathEntries = super.getClasspathEntries();
        if (CallSource.RUN_DEBUG.equals(lastCallSource)) {
            classpathEntries =
                    Arrays.stream(classpathEntries).filter(isNormalClasspathEntry).toArray(IClasspathEntry[]::new);
        }

        if (LOG.isDebugLevel()) {
            LOG.debug("Classpath entries are: {}", Objects.isNull(classpathEntries) ? "[]"
                    : Arrays.stream(classpathEntries).map(IClasspathEntry::getPath).map(IPath::toOSString).toArray());
        }
        return classpathEntries;
    }

    @Override
    protected JvmClasspathData computeClasspath(WorkProgressMonitor progressMonitor) {
        // the Java SDK will produce a list of logical classpath entries
        return bazelClasspath.getClasspathEntries(progressMonitor);
    }

    // TODO this clean() method should not be static
    public static void clean() {
        for (JvmClasspath instance : instances) {
            instance.clean();
        }
    }

    private CallSource getCallSource(StackTraceElement[] stack) {
        for (StackTraceElement elem : stack) {
            String classname = elem.getClassName();
            if (classname.endsWith(IClasspathContainerConstants.JAVA_DEBUG_DELEGATE_CMD_HANDLER)) {
                return CallSource.RUN_DEBUG;
            }
            if (classname.endsWith(IClasspathContainerConstants.JUNIT_LAUNCH_CONFIGURATION_DELEGATE)) {
                return CallSource.JUNIT;
            }
        }
        return CallSource.UNDEFINED;
    }

}
