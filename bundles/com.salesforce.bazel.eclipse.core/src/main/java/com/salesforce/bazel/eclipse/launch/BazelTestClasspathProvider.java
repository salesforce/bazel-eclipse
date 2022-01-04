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
package com.salesforce.bazel.eclipse.launch;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.StandardClasspathProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmTestClasspathHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;

/**
 * Provide the classpath for JUnit tests. These are obtained from the test rule's generated param files that list the
 * exact order of jars that the bazel test runner uses.
 */
public class BazelTestClasspathProvider extends StandardClasspathProvider {
    private static final LogHelper LOG = LogHelper.log(BazelTestClasspathProvider.class);

    public static final String BAZEL_SOURCEPATH_PROVIDER =
            "com.salesforce.bazel.eclipse.launchconfig.sourcepathProvider";
    public static final String BAZEL_CLASSPATH_PROVIDER = "com.salesforce.bazel.eclipse.launchconfig.classpathProvider";

    // suppresses a common error dialog
    private static int javaTestDialogSkipCount = 0;

    // computeUnresolvedClassPathEntries() is called multiple times while trying to run a single test,
    // we need this variable to keep track of when to open the error dialog
    public static AtomicBoolean canOpenErrorDialog = new AtomicBoolean(true);

    // collaborator for retrieving/analyzing Bazel test param files
    BazelJvmTestClasspathHelper bazelJvmTestClasspathHelper = new BazelJvmTestClasspathHelper();
        
    /**
     * Compute classpath entries for test
     */
    @Override
    public IRuntimeClasspathEntry[] computeUnresolvedClasspath(ILaunchConfiguration configuration)
            throws CoreException {
        return computeUnresolvedClasspath(configuration, false);
    }

    /**
     * Resolve classpath entries
     */
    @Override
    public IRuntimeClasspathEntry[] resolveClasspath(IRuntimeClasspathEntry[] entries,
            ILaunchConfiguration configuration) throws CoreException {
        List<IRuntimeClasspathEntry> result = new ArrayList<>();

        for (IRuntimeClasspathEntry entry : entries) {
            IRuntimeClasspathEntry[] resolved = JavaRuntime.resolveRuntimeClasspathEntry(entry, configuration);
            Collections.addAll(result, resolved);
        }

        Collections.addAll(result, JavaRuntime.resolveSourceLookupPath(entries, configuration));

        IRuntimeClasspathEntry[] resolvedClasspath = result.toArray(new IRuntimeClasspathEntry[result.size()]);
        LOG.info("Test classpath: {}", (Object[]) resolvedClasspath);

        return resolvedClasspath;
    }

    /**
     * Return the classpath entries needed to run the test
     *
     * @param configuration
     * @param isSource
     *            - calculate binary (false) or source (true) entries
     * @return
     * @throws CoreException
     * @throws InterruptedException
     * @throws InvocationTargetException
     */
    IRuntimeClasspathEntry[] computeUnresolvedClasspath(ILaunchConfiguration configuration, boolean isSource)
            throws CoreException {
        IJavaProject project = JavaRuntime.getJavaProject(configuration);
        String projectName = project.getProject().getName();
        BazelWorkspace bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
        BazelProjectManager bazelProjectManager = ComponentContext.getInstance().getProjectManager();
        BazelProject bazelProject = bazelProjectManager.getProject(projectName);
        String testClassName = configuration.getAttribute("org.eclipse.jdt.launching.MAIN_TYPE", (String) null);
        BazelProjectTargets targets = bazelProjectManager.getConfiguredBazelTargets(bazelProject, false);
        File execRootDir = bazelWorkspace.getBazelExecRootDirectory();

        // look for the param files for the test classname and/or targets
        // each param file contains a Bazel specific list of data used when launching the test
        BazelJvmTestClasspathHelper.ParamFileResult testParamFilesResult = bazelJvmTestClasspathHelper
                .findParamFilesForTests(bazelWorkspace, bazelProject, isSource, testClassName, targets);
        
        return computeUnresolvedClasspathFromParamFiles(execRootDir, testParamFilesResult);
    }
    
    /**
     * Return the classpath entries needed to run the tests, using the Bazel param files for the
     * targets as input
     */ 
    IRuntimeClasspathEntry[] computeUnresolvedClasspathFromParamFiles(File execRootDir, 
            BazelJvmTestClasspathHelper.ParamFileResult testParamFilesResult) throws CoreException {
        List<IRuntimeClasspathEntry> result = new ArrayList<>();

        // assemble the de-duplicated list of jar files that are used across all the test targets
        // these paths are listed in the param files, and are relative to the Bazel workspace exec root
        Set<String> jarPaths = bazelJvmTestClasspathHelper.aggregateJarFilesFromParamFiles(testParamFilesResult);
        for (String rawPath : jarPaths) {
            String canonicalPath = FSPathHelper.getCanonicalPathStringSafely(new File(execRootDir, rawPath));
            IPath eachPath = new Path(canonicalPath);
            if (eachPath.toFile().exists()) {
                IRuntimeClasspathEntry src = JavaRuntime.newArchiveRuntimeClasspathEntry(eachPath);
                result.add(src);
            }
        }

        // if there was a test target that had no param file, it is an unrunnable test
        if (!testParamFilesResult.unrunnableLabels.isEmpty()) {
            StringBuffer unrunnableLabelsString = new StringBuffer();
            for (String label : testParamFilesResult.unrunnableLabels) {
                unrunnableLabelsString.append(label);
                unrunnableLabelsString.append(" ");
            }
            showUnrunnableErrorDialog(unrunnableLabelsString);
        }

        return result.toArray(new IRuntimeClasspathEntry[result.size()]);
    }
    
    /**
     * Add the classpath providers to the configuration
     *
     * @param wc
     */
    public static void enable(ILaunchConfigurationWorkingCopy wc) {
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER, BAZEL_CLASSPATH_PROVIDER);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER, BAZEL_SOURCEPATH_PROVIDER);
    }

    /**
     * Add the classpath providers to the configuration
     *
     * @param config
     * @throws CoreException
     */
    public static void enable(ILaunchConfiguration config) throws CoreException {
        if (config instanceof ILaunchConfigurationWorkingCopy) {
            enable((ILaunchConfigurationWorkingCopy) config);
        } else {
            ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
            enable(wc);
            wc.doSave();
        }
    }
    
    private void showUnrunnableErrorDialog(StringBuffer unrunnableLabelsString) {
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                if (canOpenErrorDialog.get()) {
                    canOpenErrorDialog.set(false);

                    // only present the dialog once every ~10 times; often the user will
                    // be "yeah yeah, just run the tests that you can find" when this happens
                    // because if they run the tests over a project this can happen every time
                    // TODO create a pref for a user to be able to ignore these errors
                    javaTestDialogSkipCount++;
                    if (javaTestDialogSkipCount > 1) {
                        if (javaTestDialogSkipCount > 10) {
                            // trigger it next time
                            javaTestDialogSkipCount = 0;
                        }
                        return;
                    }

                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            MessageDialog.openError(Display.getDefault().getActiveShell(), "Unknown Target",
                                "One or more of the targets being executed are not part of a Bazel java_test target ( "
                                        + unrunnableLabelsString + "). The target(s) will be ignored.\n\n"
                                        + "Since this might be a common issue for your workspace, this dialog "
                                        + "will only be presented periodically when this happens.");
                        }
                    });
                }
            }
        });
    }
}
