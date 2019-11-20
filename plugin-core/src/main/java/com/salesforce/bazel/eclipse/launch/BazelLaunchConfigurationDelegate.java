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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IVMConnector;
import org.eclipse.jdt.launching.JavaRuntime;

import com.google.common.collect.ImmutableMap;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.command.BazelCommandBuilder;
import com.salesforce.bazel.eclipse.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.eclipse.command.Command;
import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationSupport.BazelLaunchConfigAttributes;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.TargetKind;

/**
 * Runs a previously configured Bazel target.
 * 
 * @author stoens
 * @since summer 2019
 */
public class BazelLaunchConfigurationDelegate implements ILaunchConfigurationDelegate {

    static final String ID = "com.salesforce.bazel.eclipse.launch";
    static final String DEBUG_LISTENER_MSG = "Listening for transport dt_socket at address: ";

    static final LogHelper LOG = LogHelper.log(BazelLaunchConfigurationDelegate.class);

    private static int DEBUG_PORT = getAvailablePort();
    private static String DEBUG_HOST = "localhost";

    private static final BazelLaunchConfigurationSupport support = new BazelLaunchConfigurationSupport();

    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
            throws CoreException {

        boolean isDebugMode = mode.equalsIgnoreCase(ILaunchManager.DEBUG_MODE);
        String projectName = getAttributeValue(configuration, BazelLaunchConfigAttributes.PROJECT);
        Map<String, String> bazelArgs = getAttributeMap(configuration, BazelLaunchConfigAttributes.INTERNAL_BAZEL_ARGS);
        BazelLabel label = new BazelLabel(getAttributeValue(configuration, BazelLaunchConfigAttributes.LABEL));
        String targetKindStr = getAttributeValue(configuration, BazelLaunchConfigAttributes.TARGET_KIND);
        TargetKind targetKind = TargetKind.valueOfIgnoresCaseRequiresMatch(targetKindStr);
        IProject project = getProject(projectName);
        BazelWorkspaceCommandRunner bazelRunner = getBazelWorkspaceCommandRunner(project);

        Command cmd = new BazelCommandBuilder(bazelRunner, label, targetKind, bazelArgs)
                .setDebugMode(isDebugMode, DEBUG_HOST, DEBUG_PORT).build();
        ProcessBuilder processBuilder = cmd.getProcessBuilder();

        List<String> commandTokens = processBuilder.command();
        LOG.info("Launching Bazel: " + String.join(" ", commandTokens));

        launchExec(configuration, project, commandTokens, processBuilder, launch, monitor);
    }

    // OVERRIDABLE FOR TESTS

    protected IProject getProject(String projectName) {
        return BazelPluginActivator.getResourceHelper().getEclipseWorkspaceRoot().getProject(projectName);
    }

    protected BazelWorkspaceCommandRunner getBazelWorkspaceCommandRunner(IProject project) {
        return support.getBazelCommandRunnerForProject(project);
    }

    protected void launchExec(ILaunchConfiguration configuration, IProject project, List<String> commandTokens,
            ProcessBuilder processBuilder, ILaunch launch, IProgressMonitor monitor) throws CoreException {
        Process process =
                DebugPlugin.exec(commandTokens.toArray(new String[commandTokens.size()]), processBuilder.directory());
        IProcess debugProcess = DebugPlugin.newProcess(launch, process, "Bazel Runner");
        debugProcess.getStreamsProxy().getOutputStreamMonitor().addListener(new IStreamListener() {
            @Override
            public void streamAppended(String text, IStreamMonitor streamMonitor) {
                if (text.trim().equals(DEBUG_LISTENER_MSG + DEBUG_PORT)) {
                    connectDebugger(configuration, project, monitor, launch);
                }
            }
        });
    }

    // INTERNAL

    private static Map<String, String> getConnectorDebugArgs() {
        return ImmutableMap.of("hostname", DEBUG_HOST, "port", String.valueOf(DEBUG_PORT));
    }

    private static String getAttributeValue(ILaunchConfiguration configuration, BazelLaunchConfigAttributes attribute) {
        try {
            String value = configuration.getAttribute(attribute.getAttributeName(), (String) null);
            if (value == null || value.isEmpty()) {
                throw new IllegalStateException(
                        "Launch Configuration Attribute without value: " + attribute.getAttributeName());
            }
            return value;
        } catch (CoreException ex) {
            throw new IllegalStateException(
                    "Launch Configuration Attribute does not exist: " + attribute.getAttributeName());
        }
    }

    private static Map<String, String> getAttributeMap(ILaunchConfiguration configuration,
            BazelLaunchConfigAttributes attribute) {
        try {
            return configuration.getAttribute(attribute.getAttributeName(), Collections.emptyMap());
        } catch (CoreException ex) {
            throw new IllegalStateException(
                    "Launch Configuration Attribute does not exist: " + attribute.getAttributeName());
        }
    }

    private static void connectDebugger(ILaunchConfiguration configuration, IProject project, IProgressMonitor monitor,
            ILaunch launch) {
        try {

            // logic below copied and adapted from
            // https://github.com/eclipse/eclipse.jdt.debug/blob/master/org.eclipse.jdt.launching/launching/org/eclipse/jdt/internal/launching/JavaRemoteApplicationLaunchConfigurationDelegate.java
            IJavaProject eclipseJavaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(project);
            ISourceLookupDirector sourceLocator = new BazelJavaSourceLookupDirector(eclipseJavaProject);
            sourceLocator.initializeDefaults(configuration);
            launch.setSourceLocator(sourceLocator);

            IVMConnector connector = JavaRuntime.getDefaultVMConnector();

            connector.connect(getConnectorDebugArgs(), monitor, launch);
        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static int getAvailablePort() {
        //Possible race condition if this port is used by another process right before being used by the debugProcess
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
