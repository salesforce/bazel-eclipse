/**
 * Copyright (c) 2022, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.lang.jvm.classpath.impl.strategy;

import java.util.List;
import java.util.Set;

import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectOutputJarSet;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.util.ImplicitClasspathHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Base class for classpath strategies. A classpath strategy uses some mechanism to load classpath data for a project.
 * It is invoked by certain JvmClasspath implementations that support pluggable strategies.
 * <p>
 * Example mechanisms that can be used by classpath strategies include:<ul>
 * <li>Bazel Aspect
 * <li>Bazel Query
 * <li>Locally persisted classpath file
 * <li>Remote cached classpath file
 * </ul>
 */
public abstract class JvmClasspathStrategy {
    protected final BazelWorkspace bazelWorkspace;
    protected final BazelProjectManager bazelProjectManager;
    protected final ImplicitClasspathHelper implicitDependencyHelper;
    protected final OperatingEnvironmentDetectionStrategy osDetector;
    protected final BazelCommandManager bazelCommandManager;

    public JvmClasspathStrategy(BazelWorkspace bazelWorkspace, BazelProjectManager bazelProjectManager,
        ImplicitClasspathHelper implicitDependencyHelper, OperatingEnvironmentDetectionStrategy osDetector,
        BazelCommandManager bazelCommandManager) {
        this.bazelWorkspace = bazelWorkspace;
        this.bazelProjectManager = bazelProjectManager;
        this.implicitDependencyHelper = implicitDependencyHelper;
        this.osDetector = osDetector;
        this.bazelCommandManager = bazelCommandManager;
    }

    // API

    /**
     * Loads the classpath for a target.
     * @throws Exception
     */
    public abstract JvmClasspathData getClasspathForTarget(JvmClasspathStrategyRequest request) throws Exception;


    // INTERNAL


    /**
     * Returns the IJavaProject in the current workspace that contains at least one of the specified sources.
     */
    protected BazelProject getSourceProjectForSourcePaths(List<String> sources) {
        if (sources == null) {
            return null;
        }
        for (String candidate : sources) {
            BazelProject project = bazelProjectManager.getOwningProjectForSourcePath(bazelWorkspace, candidate);
            if (project != null) {
                return project;
            }
        }
        return null;
    }

    protected void addProjectReference(List<BazelProject> projectList, BazelProject addThis) {
        for (BazelProject existingProject : projectList) {
            if (existingProject.name.equals(addThis.name)) {
                // we already have a reference to this project
                return;
            }
        }
        projectList.add(addThis);
    }

    protected JvmClasspathEntry jarsToClasspathEntry(JVMAspectOutputJarSet jarSet, boolean isRuntimeLib,
            boolean isTestLib) {
        JvmClasspathEntry cpEntry;
        cpEntry = new JvmClasspathEntry(jarSet.getJar(), jarSet.getSrcJar(), isRuntimeLib, isTestLib);
        return cpEntry;
    }

    protected JvmClasspathEntry[] jarsToClasspathEntries(BazelWorkspace bazelWorkspace,
            WorkProgressMonitor progressMonitor, Set<JVMAspectOutputJarSet> jars, boolean isRuntimeLib,
            boolean isTestLib) {
        JvmClasspathEntry[] entries = new JvmClasspathEntry[jars.size()];
        int i = 0;
        bazelWorkspace.getBazelOutputBaseDirectory();
        bazelWorkspace.getBazelExecRootDirectory();
        for (JVMAspectOutputJarSet jar : jars) {
            entries[i] = jarsToClasspathEntry(jar, isRuntimeLib, isTestLib);
            i++;
        }
        return entries;
    }
}
