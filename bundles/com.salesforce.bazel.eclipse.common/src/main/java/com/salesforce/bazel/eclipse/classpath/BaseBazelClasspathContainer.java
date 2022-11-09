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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Computes the classpath for a Bazel package and provides it to the JDT tooling in Eclipse.
 */
public abstract class BaseBazelClasspathContainer implements IClasspathContainer {
    protected final BazelProjectManager bazelProjectManager;
    protected BazelProject bazelProject;
    protected final IPath eclipseProjectPath;
    protected final boolean eclipseProjectIsRoot;
    protected final JavaCoreHelper javaCoreHelper;
    protected final OperatingEnvironmentDetectionStrategy osDetector;
    protected final LogHelper logger;
    protected final BazelWorkspace bazelWorkspace;
    protected IClasspathEntry[] lastComputedClasspath = null;

    protected BaseBazelClasspathContainer(IProject eclipseProject, ResourceHelper resourceHelper, JavaCoreHelper jcHelper,
            BazelProjectManager bpManager, OperatingEnvironmentDetectionStrategy osDetectStrategy,
            BazelWorkspace workspace) throws IOException, InterruptedException, BackingStoreException,
            JavaModelException, BazelCommandLineToolConfigurationException {
        bazelProjectManager = bpManager;
        eclipseProjectPath = eclipseProject.getLocation();
        eclipseProjectIsRoot = resourceHelper.isBazelRootProject(eclipseProject);
        osDetector = osDetectStrategy;
        bazelWorkspace = workspace;
        javaCoreHelper = jcHelper;

        bazelProject = bazelProjectManager.getProject(eclipseProject.getName());
        if (bazelProject == null) {
            bazelProject = new BazelProject(eclipseProject.getName(), eclipseProject);
            bazelProjectManager.addProject(bazelProject);
        }

        logger = LogHelper.log(this.getClass());

    }

    @Override
    public IClasspathEntry[] getClasspathEntries() {
        long startTimeMillis = System.currentTimeMillis();
        
        // TODO this is a fake progress monitor; doesn't JDT provide a progress monitor for computing classpath? 
        // given how expensive this can be, seems like a big miss? 
        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor();

        // Fast exit - check the caller of this method to decide if we need to incur the expense of a full classpath compute
        // The saveContainers() caller is useful if we were persisting classpath data to disk for faster restarts later
        // but currently we feel that is riskier than just recomputing the classpath on restart.
        // Also, if the user is shutting down the IDE don't waste cycles computing classpaths.
        if (lastComputedClasspath != null) {
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
            for (int i = 0; i < stackTraceElements.length; i++) {
                StackTraceElement caller = stackTraceElements[i];
                if (caller.getMethodName().equals("saveContainers")) {
                    // fullname: JavaModelManager@VariablesAndContainersSaveHelper.saveContainers()

                    // the last computed classpath is good enough for saveContainers() use cases
                    return lastComputedClasspath;
                }
                if (i == 4) {
                    // we don't know exactly which index it could be, it dependes on subclassing, etc.
                    // but the saveContainers method should be in the first 5 entries
                    break;
                }
            }
        }

        /**
         * Observed behavior of Eclipse is that this method can get called multiple times before the first invocation
         * completes, therefore the cache is not as effective as it could be. Synchronize on this instance such that the
         * first invocation completes and populates the cache before the subsequent calls are allowed to proceed.
         */
        JvmClasspathData computedClasspath = null;
        List<IClasspathEntry> eclipseClasspathEntries = new ArrayList<>();
        synchronized (this) {

            // the Bazel Java SDK will produce a list of logical classpath entries
            computedClasspath = computeClasspath(progressMonitor);

            // convert the logical entries into concrete Eclipse entries
            for (JvmClasspathEntry entry : computedClasspath.jvmClasspathEntries) {
                if (entry.pathToJar != null) {
                    IPath jarPath = getIPathOnDisk(bazelWorkspace, entry.pathToJar);
                    if (jarPath != null) {
                        // srcJarPath must be relative to the workspace, by order of Eclipse
                        IPath srcJarPath = getIPathOnDisk(bazelWorkspace, entry.pathToSourceJar);
                        IPath srcJarRootPath = null;
                        eclipseClasspathEntries.add(
                            javaCoreHelper.newLibraryEntry(jarPath, srcJarPath, srcJarRootPath, entry.isTestJar));
                    }
                } else {
                    IProject iproject = (IProject) entry.bazelProject.projectImpl;
                    IPath ipath = iproject.getFullPath();
                    eclipseClasspathEntries.add(javaCoreHelper.newProjectEntry(ipath));
                }
            }
        } // end synchronized

        // Now update project refs, which includes adding new ones and removing any that may now be obsolete
        // (e.g. dep was removed, project removed from IDE workspace)
        // We need to do this outside of the synchronized block because this next statement requires a lock on the
        // Eclipse workspace, and this may take some time to acquire.
        bazelProjectManager.setProjectReferences(bazelProject, computedClasspath.classpathProjectReferences);

        lastComputedClasspath = eclipseClasspathEntries.toArray(new IClasspathEntry[] {});

        SimplePerfRecorder.addTime("classpath_getClasspathEntry", startTimeMillis);

        return lastComputedClasspath;
    }

    @Override
    public int getKind() {
        return K_APPLICATION;
    }

    @Override
    public IPath getPath() {
        return eclipseProjectPath;
    }

    // OVERRIDES

    protected abstract JvmClasspathData computeClasspath(WorkProgressMonitor progressMonitor);

    // HELPERS

    protected IPath getIPathOnDisk(BazelWorkspace bazelWorkspace, String filePathStr) {
        if (filePathStr == null) {
            return null;
        }

        File filePathFile = new File(filePathStr);
        Path absolutePath;
        String searchedLocations = "";
        if (!filePathFile.isAbsolute()) {
            // need to figure out where this relative path is on disk
            // TODO this hunting around for the right root of the relative path indicates we need to rework this
            File bazelExecRootDir = bazelWorkspace.getBazelExecRootDirectory();
            filePathFile = new File(bazelExecRootDir, filePathStr);
            if (!filePathFile.exists()) {
                searchedLocations = filePathFile.getAbsolutePath();
                File bazelOutputBase = bazelWorkspace.getBazelOutputBaseDirectory();
                filePathFile = new File(bazelOutputBase, filePathStr);
                if (!filePathFile.exists()) {
                    searchedLocations = searchedLocations + ", " + filePathFile.getAbsolutePath();
                    // java_import locations are resolved here
                    File bazelWorkspaceDir = bazelWorkspace.getBazelWorkspaceRootDirectory();
                    filePathFile = new File(bazelWorkspaceDir, filePathStr);
                    if (!filePathFile.exists()) {
                        searchedLocations = searchedLocations + ", " + filePathFile.getAbsolutePath();
                        // give up
                        filePathFile = null;
                    }
                }
            }
        }

        if (filePathFile == null) {
            // this can happen if someone does a 'bazel clean' using the command line #113
            // https://github.com/salesforce/bazel-eclipse/issues/113 $SLASH_OK url
            String msg = "Problem adding jar to project [" + bazelProject.name
                    + "] because it does not exist on the filesystem(2), searched paths: " + searchedLocations
                    + " classpath impl: " + this.getClass().getName();
            logger.error(msg);
            continueOrThrow(new IllegalArgumentException(msg));
            return null;
        }

        // we now can derive our absolute path
        absolutePath = filePathFile.toPath();

        // We have had issues with Eclipse complaining about symlinks in the Bazel output directories not being real,
        // so we resolve them before handing them back to Eclipse.
        if (Files.isSymbolicLink(absolutePath)) {
            try {
                // resolving the link will fail if the symlink does not a point to a real file
                absolutePath = Files.readSymbolicLink(absolutePath);
            } catch (IOException ex) {
                // TODO this can happen if someone does a 'bazel clean' using the command line #113
                // https://github.com/salesforce/bazel-eclipse/issues/113 $SLASH_OK url
                String msg = "Problem adding jar to project [" + bazelProject.name
                        + "] because it does not exist on the filesystem(1): " + absolutePath;
                logger.error(msg);
                continueOrThrow(new IllegalArgumentException(msg, ex));
            }
        }
        return org.eclipse.core.runtime.Path.fromOSString(absolutePath.toString());
    }

    protected void continueOrThrow(Throwable th) {
        // under real usage, we suppress fatal exceptions because sometimes there are IDE timing issues that can
        // be corrected if the classpath is computed again.
        // But under tests, we want to fail fatally otherwise tests could pass when they shouldn't
        if (osDetector.isTestRuntime()) {
            throw new IllegalStateException("The classpath could not be computed by the BazelClasspathContainer", th);
        }
    }

}
