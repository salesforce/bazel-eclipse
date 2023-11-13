/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.eclipse.core.launchconfiguration;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMConnector;
import org.eclipse.jdt.launching.JavaRuntime;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.sdk.command.BazelRunCommand;
import com.salesforce.bazel.sdk.command.shell.ShellUtil;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.util.SystemUtil;

/**
 *
 */
public class BazelRunLaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate
        implements BazelLaunchConfigurationConstants {

    /**
     * Returns the Bazel project specified by the given launch configuration, or <code>null</code> if none.
     *
     * @param configuration
     *            launch configuration
     * @return the Bazel project specified by the given launch configuration, or <code>null</code> if none
     * @exception CoreException
     *                if unable to retrieve the attribute
     */
    public BazelProject getBazelProject(ILaunchConfiguration configuration) throws CoreException {
        var name = getProjectName(configuration);
        if (name != null) {
            name = name.trim();
            if (name.length() > 0) {
                var project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
                if (project.exists() && BazelProject.isBazelProject(project)) {
                    return BazelCore.create(project);
                }
            }
        }
        return null;
    }

    /**
     * Returns the Bazel target label specified by the given launch configuration, or <code>null</code> if none.
     *
     * @param configuration
     *            launch configuration
     * @return the Bazel target label specified by the given launch configuration, or <code>null</code> if none
     * @exception CoreException
     *                if unable to retrieve the attribute
     */
    public BazelLabel getBazelTarget(ILaunchConfiguration configuration) throws CoreException {
        var labelString = configuration.getAttribute(TARGET_LABEL, (String) null);
        return labelString != null ? new BazelLabel(labelString) : null;
    }

    private IVMConnector getConnector(ILaunchConfiguration configuration) throws CoreException {
        var connector = JavaRuntime.getVMConnector(
            org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ID_SOCKET_ATTACH_VM_CONNECTOR);
        if (connector == null) {
            throw new CoreException(
                    Status.error("Socket attach connector is not available! Unable to connect remote debugger!"));
        }
        return connector;
    }

    private Map<String, String> getConnectorArgs(ILaunchConfiguration configuration) throws CoreException {
        var argMap = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, new HashMap<>());
        var connectTimeout = getConnectTimeoutFromPreferences();
        argMap.put("timeout", Integer.toString(connectTimeout));
        argMap.put("hostname", "localhost");
        argMap.put("port", Integer.toString(5005));
        return argMap;
    }

    private int getConnectTimeoutFromPreferences() {
        return Platform.getPreferencesService()
                .getInt(
                    "org.eclipse.jdt.launching",
                    JavaRuntime.PREF_CONNECT_TIMEOUT,
                    JavaRuntime.DEF_CONNECT_TIMEOUT,
                    null);
    }

    private String getProjectName(ILaunchConfiguration configuration) throws CoreException {
        return configuration.getAttribute(PROJECT_NAME, (String) null);
    }

    @Override
    protected IProject[] getProjectsForProblemSearch(ILaunchConfiguration configuration, String mode)
            throws CoreException {
        var bazelProject = getBazelProject(configuration);
        if (bazelProject == null) {
            return null;
        }

        List<IProject> projects = bazelProject.getBazelWorkspace()
                .getBazelProjects()
                .stream()
                .map(BazelProject::getProject)
                .collect(toList());
        return projects.toArray(new IProject[projects.size()]);
    }

    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor progress)
            throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, "Launching " + configuration.getName(), 3);

            var bazelProject = getBazelProject(configuration);
            if (bazelProject == null) {
                throw new CoreException(Status.error(format("Project '%s' not found!", getProjectName(configuration))));
            }

            var bazelWorkspace = bazelProject.getBazelWorkspace();
            if (bazelWorkspace == null) {
                throw new CoreException(Status.error(format("Project '%s' not found!", getProjectName(configuration))));
            }

            var bazelTarget = getBazelTarget(configuration);
            if (bazelTarget == null) {
                throw new CoreException(Status.error("No target configured!"));
            }

            var targetArgs = new ArrayList<String>();

            var attachDebugger = ILaunchManager.DEBUG_MODE.equals(mode);
            if (attachDebugger) {
                targetArgs.add(0, "--debug");
            }

            var workspaceRoot = bazelWorkspace.getLocation().toPath();
            var command =
                    new BazelRunCommand(bazelTarget.toPrimitive(), targetArgs, workspaceRoot, configuration.getName());

            monitor.subTask("Staring Bazel");
            var bazelBinary = bazelWorkspace.getCommandExecutor().selectBazelBinary(bazelWorkspace);
            IProcess bazelProcess;
            try {
                List<String> commandLine = new ArrayList<>(command.prepareCommandLine(bazelBinary.bazelVersion()));
                commandLine.add(0, bazelBinary.executable().toString());

                if (SystemUtil.getInstance().isMac()) {
                    commandLine = new ShellUtil().wrapExecutionIntoShell(commandLine);
                }

                var pb = new ProcessBuilder(commandLine);
                pb.directory(workspaceRoot.toFile());
                setEnvironment(pb, configuration);
                var p = pb.start();
                bazelProcess = DebugPlugin.newProcess(launch, p, mode);

                monitor.worked(1);

                // check for cancellation
                if (monitor.isCanceled()) {
                    for (IProcess process : launch.getProcesses()) {
                        if (process.canTerminate()) {
                            process.terminate();
                        }
                    }
                    return;
                }
            } catch (IOException e) {
                throw new CoreException(Status.error("Error launching the underlying Bazel process.", e));
            }

            if (attachDebugger) {
                monitor.subTask("Connecting Java Debugger");

                // get the connector
                var connector = getConnector(configuration);
                var argMap = getConnectorArgs(configuration);

                // set the default source locator if required
                setDefaultSourceLocator(launch, configuration);

                monitor.worked(1);

                // wait for bazel to be ready
                waitForJdwpPort(Duration.ofMinutes(5), monitor.split(1, SubMonitor.SUPPRESS_NONE), bazelProcess);

                // connect to remote VM
                if (!monitor.isCanceled()) {
                    connector.connect(argMap, monitor, launch);
                }

                // check for cancellation
                if (monitor.isCanceled()) {
                    for (IDebugTarget target : launch.getDebugTargets()) {
                        if (target.canDisconnect()) {
                            target.disconnect();
                        }
                    }
                    for (IProcess process : launch.getProcesses()) {
                        if (process.canTerminate()) {
                            process.terminate();
                        }
                    }
                }
            }
        } finally {
            if (progress != null) {
                progress.done();
            }
        }

    }

    private void setEnvironment(ProcessBuilder pb, ILaunchConfiguration configuration) throws CoreException {
        var envp = getEnvironment(configuration);
        if (envp != null) {
            var env = pb.environment();
            env.clear();
            for (String e : envp) {
                var index = e.indexOf('=');
                if (index != -1) {
                    env.put(e.substring(0, index), e.substring(index + 1));
                }
            }
        }
    }

    private void waitForJdwpPort(Duration duration, SubMonitor monitor, IProcess bazelProcess) {
        var timeout = Instant.now().plus(duration);
        monitor.beginTask("Waiting for JDWP", IProgressMonitor.UNKNOWN);
        while (Instant.now().isBefore(timeout) && !monitor.isCanceled() && !bazelProcess.isTerminated()) {
            try (var s = new Socket()) {
                s.bind(null);
                s.connect(new InetSocketAddress("localhost", 5005), 500);
                return; // success
            } catch (IOException e) {
                if (monitor.isCanceled()) {
                    return;
                }
            }
        }
        monitor.done();
    }

}
