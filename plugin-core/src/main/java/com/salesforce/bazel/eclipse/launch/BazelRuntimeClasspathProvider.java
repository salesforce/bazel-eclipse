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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.StandardClasspathProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.command.BazelCommandFacade;
import com.salesforce.bazel.eclipse.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;

/**
 * Provide the runtime classpath for JUnit tests. These are obtained from the test rule's generated param files that
 * list the exact order of jars that the bazel test runner uses.
 * 
 * @author Blaine Buxton
 *
 */
public class BazelRuntimeClasspathProvider extends StandardClasspathProvider {
    public static final String BAZEL_SOURCEPATH_PROVIDER =
            "com.salesforce.bazel.eclipse.launchconfig.sourcepathProvider";
    public static final String BAZEL_CLASSPATH_PROVIDER = "com.salesforce.bazel.eclipse.launchconfig.classpathProvider";

    static final String BAZEL_DEPLOY_PARAMS_SUFFIX = "_deploy.jar-0.params";
    static final String BAZEL_SRC_DEPLOY_PARAMS_SUFFIX = "_deploy-src.jar-0.params";

    private static final Bundle BUNDLE = FrameworkUtil.getBundle(BazelRuntimeClasspathProvider.class);
    
    // computeUnresolvedClassPathEntries() is called multiple times while trying to run a single test, 
    // we need this variable to keep track of when to open the error dialog
    public static AtomicBoolean canOpen = new AtomicBoolean(true); 
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
            for (IRuntimeClasspathEntry resolvedEntry : resolved) {
                result.add(resolvedEntry);
            }
        }
        
        for (IRuntimeClasspathEntry entry : JavaRuntime.resolveSourceLookupPath(entries, configuration)) {
            result.add(entry);
        }
        
        return result.toArray(new IRuntimeClasspathEntry[result.size()]);

    }

    /**
     * Return the classpath entries needed to run the test
     * 
     * @param configuration
     * @param isSource
     *            - calculate binary or source entries
     * @return
     * @throws CoreException
     * @throws InterruptedException 
     * @throws InvocationTargetException 
     */
    IRuntimeClasspathEntry[] computeUnresolvedClasspath(ILaunchConfiguration configuration, boolean isSource)
            throws CoreException{
        List<IRuntimeClasspathEntry> result = new ArrayList<>();
        IJavaProject project = JavaRuntime.getJavaProject(configuration);
        File base = getBazelWorkspaceExecRoot(project);

        String testClassName = configuration.getAttribute("org.eclipse.jdt.launching.MAIN_TYPE", (String) null);
        String suffix = getParamsJarSuffix(isSource);

        String paramsName = testClassName.replace('.', '/') + suffix;

        List<String> targets = BazelEclipseProjectSupport.getBazelTargetsForEclipseProject(project.getProject(), false);
        for (String eachTarget : targets) {
            File paramsFile = findParamsJar(project, paramsName, eachTarget);
            if (!paramsFile.exists()) {
                Display.getDefault().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        if(canOpen.get()) {
                            canOpen.set(false);
                            MessageDialog.openError(Display.getDefault().getActiveShell(), "Unknown Target", "One or all of the tests trying to be executed are not part of a Bazel java_test target");
                        }
                    }
                });
                continue;
            }
            List<String> jarPaths;
            try {
                jarPaths = getPathsToJars(paramsFile);
            } catch (IOException e) {
                throw new CoreException(new Status(Status.ERROR, BUNDLE.getSymbolicName(),
                        "Error parsing " + paramsFile.getAbsolutePath(), e));
            }
            for (String rawPath : jarPaths) {
                IPath eachPath = new Path(new File(base, rawPath).getAbsolutePath());
                if (eachPath.toFile().exists()) {
                    IRuntimeClasspathEntry src = JavaRuntime.newArchiveRuntimeClasspathEntry(eachPath);
                    result.add(src);
                }
            }
        }
        return result.toArray(new IRuntimeClasspathEntry[result.size()]);
    }

    String getParamsJarSuffix(boolean isSource) {
        String suffix = BAZEL_DEPLOY_PARAMS_SUFFIX;

        if (isSource) {
            suffix = BAZEL_SRC_DEPLOY_PARAMS_SUFFIX;
        }
        return suffix;
    }

    /**
     * This needs to be re-implemented - the path is hardcoded. It should be path of the test rule TODO - Remove
     * hardcoded src/test/java
     * 
     * @param project
     * @param paramsName
     * @param target
     * @return
     */
    File findParamsJar(IJavaProject project, String paramsName, String target) {
        String targetPath = target.split(":")[0];
        // testJar for bazel's iterative test rules
        File paramsFile = new File(new File(new File(getBazelBin(project), targetPath), "src/test/java"), paramsName);
        if (!paramsFile.exists()) {
            // testJar for single test rule
            //TODO: Add support for test rules where testName is not the same as testClass 
            String testJarName = paramsName.substring(paramsName.lastIndexOf(File.separator)+1);
            paramsFile = new File(new File(getBazelBin(project), targetPath), testJarName);
        }
        return paramsFile;
    }

    /**
     * Obtain the output base for the project as defined by bazel
     * 
     * @param project
     * @return
     */
    File getBazelWorkspaceExecRoot(IJavaProject project) throws CoreException {
        BazelCommandFacade bazelCommandFacade = BazelPluginActivator.getBazelCommandFacade();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandFacade.getWorkspaceCommandRunner(BazelPluginActivator.getBazelWorkspaceRootDirectory());
        
        return bazelWorkspaceCmdRunner.getBazelWorkspaceOutputBase(null);
    }

    /**
     * Obtain the bazel-bin directory under the workspace root
     * 
     * @param project
     * @return
     */
    File getBazelBin(IJavaProject project) {
        BazelCommandFacade bazelCommandFacade = BazelPluginActivator.getBazelCommandFacade();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandFacade.getWorkspaceCommandRunner(BazelPluginActivator.getBazelWorkspaceRootDirectory());
        
        return bazelWorkspaceCmdRunner.getBazelWorkspaceBin(null);
    }

    /**
     * Parse the jars from the given params file
     * 
     * @param paramsFile
     * @return
     * @throws IOException
     */
    List<String> getPathsToJars(File paramsFile) throws IOException {
        try (Scanner scanner = new Scanner(paramsFile)) {
            return getPathsToJars(scanner);
        }
    }

    /**
     * Gets the paths from lines from scanner
     * 
     * @param scanner
     * @return
     */
    List<String> getPathsToJars(Scanner scanner) {
        List<String> result = new ArrayList<>();
        boolean addToResult = false;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (addToResult && !line.startsWith("--")) {
                String jar = line.split(",")[0];
                if (jar.endsWith(".jar")) {
                    result.add(jar);
                }
            } else {
                addToResult = false;
            }
            if (line.startsWith("--output")) {
                addToResult = true;
                continue;
            }
            if (line.startsWith("--sources")) {
                addToResult = true;
                continue;
            }
        }
        return result;
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
}
