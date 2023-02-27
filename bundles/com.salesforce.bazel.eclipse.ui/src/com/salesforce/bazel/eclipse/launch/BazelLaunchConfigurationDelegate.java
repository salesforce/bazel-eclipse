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
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.JavaRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationSupport.BazelLaunchConfigAttributes;
import com.salesforce.bazel.sdk.command.BazelProcessBuilder;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelTargetKind;

/**
 * Runs a Bazel non-test target, like a java_binary.
 */
public class BazelLaunchConfigurationDelegate implements ILaunchConfigurationDelegate {

    static final String ID = "com.salesforce.bazel.eclipse.launch";
    static final String DEBUG_LISTENER_MSG = "Listening for transport dt_socket at address: ";

    private static Logger LOG = LoggerFactory.getLogger(BazelLaunchConfigurationDelegate.class);

    private static int DEBUG_PORT = getAvailablePort();
    private static String DEBUG_HOST = "localhost";

    private static void connectDebugger(ILaunchConfiguration configuration, IProject project, IProgressMonitor monitor,
            ILaunch launch) {
        // logic below copied and adapted from
        // https://github.com/eclipse/eclipse.jdt.debug/blob/master/org.eclipse.jdt.launching/launching/org/eclipse/jdt/internal/launching/JavaRemoteApplicationLaunchConfigurationDelegate.java ($SLASH_OK url)

        var mainProject = ComponentContext.getInstance().getJavaCoreHelper().getJavaProjectForProject(project);
        var otherProjects = getOtherJavaProjects(mainProject);
        ISourceLookupDirector sourceLocator = new BazelJavaSourceLookupDirector(mainProject, otherProjects);
        try {
            sourceLocator.initializeDefaults(configuration);
            launch.setSourceLocator(sourceLocator);
            var connector = JavaRuntime.getDefaultVMConnector();
            connector.connect(getConnectorDebugArgs(), monitor, launch);
        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // OVERRIDABLE FOR TESTS

    private static int getAvailablePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, String> getConnectorDebugArgs() {
        Map<String, String> map = new HashMap<>();
        map.put("hostname", DEBUG_HOST);
        map.put("port", String.valueOf(DEBUG_PORT));
        return map;
    }

    // INTERNAL

    private static List<IJavaProject> getOtherJavaProjects(IJavaProject mainProject) {
        return Arrays.stream(ComponentContext.getInstance().getJavaCoreHelper().getAllBazelJavaProjects(false))
                .filter(p -> p != mainProject).collect(Collectors.toList());
    }

    protected IProject getProject(String projectName) {
        return ComponentContext.getInstance().getResourceHelper().getProjectByName(projectName);
    }

    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
            throws CoreException {

        var isDebugMode = ILaunchManager.DEBUG_MODE.equalsIgnoreCase(mode);
        var projectName = BazelLaunchConfigAttributes.PROJECT.getStringValue(configuration);
        var label = new BazelLabel(BazelLaunchConfigAttributes.LABEL.getStringValue(configuration));
        var targetKindStr = BazelLaunchConfigAttributes.TARGET_KIND.getStringValue(configuration);
        if (targetKindStr == null) {
            LOG.error("Target is of unknown type. Cannot create a launcher.");
            return;
        }
        var targetKind = BazelTargetKind.valueOfIgnoresCaseRequiresMatch(targetKindStr);
        var project = ComponentContext.getInstance().getResourceHelper().getProjectByName(projectName);

        List<String> allArgs =
                new ArrayList<>(BazelLaunchConfigAttributes.INTERNAL_BAZEL_ARGS.getListValue(configuration));
        allArgs.addAll(BazelLaunchConfigAttributes.USER_BAZEL_ARGS.getListValue(configuration));

        if (targetKind.isRunnable()) {
            // build before running - this is required because building generates the shell script
            // we end up running
            project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
        }

        var bazelCommandRunner = EclipseBazelWorkspaceContext.getInstance().getWorkspaceCommandRunner();

        var cmd = bazelCommandRunner.getBazelLauncherBuilder().setLabel(label).setTargetKind(targetKind)
                .setArgs(allArgs).setDebugMode(isDebugMode, DEBUG_HOST, DEBUG_PORT).build();
        var processBuilder = cmd.getProcessBuilder();

        var commandTokens = processBuilder.command();
        LOG.info("Launching Bazel: " + String.join(" ", commandTokens));

        launchExec(configuration, project, commandTokens, processBuilder, launch, monitor);
    }

    protected void launchExec(ILaunchConfiguration configuration, IProject project, List<String> commandTokens,
            BazelProcessBuilder processBuilder, ILaunch launch, IProgressMonitor monitor) throws CoreException {

        var cmdLine = commandTokens.toArray(new String[commandTokens.size()]);
        var workingDirectory = processBuilder.directory();

        var resourceHelper = ComponentContext.getInstance().getResourceHelper();

        // launch the external process, and attach to the output
        var process = resourceHelper.exec(cmdLine, workingDirectory);
        var debugProcess = resourceHelper.newProcess(launch, process, "Bazel Runner");
        var streamsProxy = debugProcess.getStreamsProxy();
        if (streamsProxy != null) {
            // in mock testing envs, streamsProxy will be null
            streamsProxy.getOutputStreamMonitor().addListener((text, streamMonitor) -> {
                if ((DEBUG_LISTENER_MSG + DEBUG_PORT).equals(text.trim())) {
                    connectDebugger(configuration, project, monitor, launch);
                }
            });
        }
    }
}
