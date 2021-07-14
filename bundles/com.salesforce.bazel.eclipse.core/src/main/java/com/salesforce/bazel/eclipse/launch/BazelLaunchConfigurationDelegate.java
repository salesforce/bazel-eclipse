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
package com.salesforce.bazel.eclipse.launch;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IVMConnector;
import org.eclipse.jdt.launching.JavaRuntime;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationSupport.BazelLaunchConfigAttributes;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelProcessBuilder;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.Command;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelTargetKind;

/**
 * Runs a Bazel non-test target, like a java_binary.
 */
public class BazelLaunchConfigurationDelegate implements ILaunchConfigurationDelegate {

    static final String ID = "com.salesforce.bazel.eclipse.launch";
    static final String DEBUG_LISTENER_MSG = "Listening for transport dt_socket at address: ";

    static final LogHelper LOG = LogHelper.log(BazelLaunchConfigurationDelegate.class);

    private static int DEBUG_PORT = getAvailablePort();
    private static String DEBUG_HOST = "localhost";

    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
            throws CoreException {

        boolean isDebugMode = mode.equalsIgnoreCase(ILaunchManager.DEBUG_MODE);
        String projectName = BazelLaunchConfigAttributes.PROJECT.getStringValue(configuration);
        BazelLabel label = new BazelLabel(BazelLaunchConfigAttributes.LABEL.getStringValue(configuration));
        String targetKindStr = BazelLaunchConfigAttributes.TARGET_KIND.getStringValue(configuration);
        if (targetKindStr == null) {
            LOG.error("Target is of unknown type. Cannot create a launcher.");
            return;
        }
        BazelTargetKind targetKind = BazelTargetKind.valueOfIgnoresCaseRequiresMatch(targetKindStr);
        IProject project = BazelPluginActivator.getResourceHelper().getProjectByName(projectName);

        List<String> allArgs = new ArrayList<>();
        allArgs.addAll(BazelLaunchConfigAttributes.INTERNAL_BAZEL_ARGS.getListValue(configuration));
        allArgs.addAll(BazelLaunchConfigAttributes.USER_BAZEL_ARGS.getListValue(configuration));

        if (targetKind.isRunnable()) {
            // build before running - this is required because building generates the shell script
            // we end up running
            project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        }

        BazelWorkspaceCommandRunner bazelCommandRunner = BazelPluginActivator.getInstance().getWorkspaceCommandRunner();

        Command cmd = bazelCommandRunner.getBazelLauncherBuilder().setLabel(label).setTargetKind(targetKind)
                .setArgs(allArgs).setDebugMode(isDebugMode, DEBUG_HOST, DEBUG_PORT).build();
        BazelProcessBuilder processBuilder = cmd.getProcessBuilder();

        List<String> commandTokens = processBuilder.command();
        LOG.info("Launching Bazel: " + String.join(" ", commandTokens));

        launchExec(configuration, project, commandTokens, processBuilder, launch, monitor);
    }

    // OVERRIDABLE FOR TESTS

    protected IProject getProject(String projectName) {
        return BazelPluginActivator.getResourceHelper().getProjectByName(projectName);
    }

    protected void launchExec(ILaunchConfiguration configuration, IProject project, List<String> commandTokens,
            BazelProcessBuilder processBuilder, ILaunch launch, IProgressMonitor monitor) throws CoreException {

        String[] cmdLine = commandTokens.toArray(new String[commandTokens.size()]);
        File workingDirectory = processBuilder.directory();

        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();

        // launch the external process, and attach to the output
        Process process = resourceHelper.exec(cmdLine, workingDirectory);
        IProcess debugProcess = resourceHelper.newProcess(launch, process, "Bazel Runner");
        IStreamsProxy streamsProxy = debugProcess.getStreamsProxy();
        if (streamsProxy != null) {
            // in mock testing envs, streamsProxy will be null
            streamsProxy.getOutputStreamMonitor().addListener(new IStreamListener() {
                @Override
                public void streamAppended(String text, IStreamMonitor streamMonitor) {
                    if (text.trim().equals(DEBUG_LISTENER_MSG + DEBUG_PORT)) {
                        connectDebugger(configuration, project, monitor, launch);
                    }
                }
            });
        }
    }

    // INTERNAL

    private static Map<String, String> getConnectorDebugArgs() {
        Map<String, String> map = new HashMap<>();
        map.put("hostname", DEBUG_HOST);
        map.put("port", String.valueOf(DEBUG_PORT));
        return map;
    }

    private static void connectDebugger(ILaunchConfiguration configuration, IProject project, IProgressMonitor monitor,
            ILaunch launch) {
        // logic below copied and adapted from
        // https://github.com/eclipse/eclipse.jdt.debug/blob/master/org.eclipse.jdt.launching/launching/org/eclipse/jdt/internal/launching/JavaRemoteApplicationLaunchConfigurationDelegate.java ($SLASH_OK url)

        IJavaProject mainProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(project);
        List<IJavaProject> otherProjects = getOtherJavaProjects(mainProject);
        ISourceLookupDirector sourceLocator = new BazelJavaSourceLookupDirector(mainProject, otherProjects);
        try {
            sourceLocator.initializeDefaults(configuration);
            launch.setSourceLocator(sourceLocator);
            IVMConnector connector = JavaRuntime.getDefaultVMConnector();
            connector.connect(getConnectorDebugArgs(), monitor, launch);
        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static List<IJavaProject> getOtherJavaProjects(IJavaProject mainProject) {
        return Arrays.stream(BazelPluginActivator.getJavaCoreHelper().getAllBazelJavaProjects(false))
                .filter(p -> p != mainProject).collect(Collectors.toList());
    }

    private static int getAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
