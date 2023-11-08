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
package com.salesforce.bazel.eclipse.ui.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.extensions.ExtensibleCommandExecutor;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.BazelCommand;

/**
 * A job to collect and print diagnostics information for Bazel execution.
 */
final class BazelDiagnosticsJob extends WorkspaceJob {
    
    static class ArbitraryCommand extends BazelCommand<Void> {
        
        private final Path executable;
        
        public ArbitraryCommand(Path executable, List<String> commandArgs, Path workspaceRoot, String purpose) {
            super("arbitrary", workspaceRoot, purpose);
            this.executable = executable;
            setCommandArgs(commandArgs.toArray(new String[commandArgs.size()]));
        }
        
        @Override
        protected void appendToStringDetails(ArrayList<String> toStringCommandLine) {
            // empty
        }
        
        @Override
        protected Void doGenerateResult() throws IOException {
            // no-op
            return null;
        }
        
        @Override
        public Void generateResult(int exitCode) throws IOException {
            // no-op
            return null;
        }
        
        @Override
        public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
            var commandLine = new ArrayList<>(getStartupArgs());
            commandLine.addAll(getCommandArgs());
            return commandLine;
        }
        
        @Override
        public void setBazelBinary(BazelBinary bazelBinary) {
            // force our executable
            super.setBazelBinary(new BazelBinary(executable, bazelBinary.bazelVersion()));
        }
        
        @Override
        protected boolean supportsInjectionOfAdditionalBazelOptions() {
            return false;
        }
    }
    
    /**
     *
     */
    public BazelDiagnosticsJob() {
        super("Collect Bazel diagnostics");
    }

    /**
     * @param name
     */
    private BazelDiagnosticsJob(String name) {
        super(name);
        setRule(ResourcesPlugin.getWorkspace().getRuleFactory().buildRule());
        setUser(true);

    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        try {
            var executor = new ExtensibleCommandExecutor();
            var workingDir = Path.of(System.getProperty("user.home"));

            executor.execute(
                new ArbitraryCommand(
                        Path.of("echo"),
                        List.of("$SHELL", System.getenv("SHELL")),
                        workingDir,
                        "Shell environment"),
                monitor::isCanceled);
            executor.execute(
                new ArbitraryCommand(
                        Path.of("sysctl"),
                        List.of("-in", "sysctl.proc_translated"),
                        workingDir,
                        "macOS environment"),
                monitor::isCanceled);

            executor.execute(
                new ArbitraryCommand(Path.of("which"), List.of("bazel"), workingDir, "Which Bazel"),
                monitor::isCanceled);
            executor.execute(
                new ArbitraryCommand(Path.of("bazel"), List.of("version"), workingDir, "Bazel version"),
                monitor::isCanceled);

            var bazelWorkspaces = BazelCore.getModel().getBazelWorkspaces();
            for (BazelWorkspace bazelWorkspace : bazelWorkspaces) {
                var workspaceRoot = bazelWorkspace.getLocation().toPath();
                executor.execute(
                    new ArbitraryCommand(
                            Path.of("bazel"),
                            List.of("info"),
                            workspaceRoot,
                            bazelWorkspace.getName()),
                    monitor::isCanceled);
                executor.execute(
                    new ArbitraryCommand(
                            Path.of("echo"),
                            List.of(workspaceRoot.toString()),
                            workspaceRoot,
                            "working directory"),
                    monitor::isCanceled);
                executor.execute(
                    new ArbitraryCommand(Path.of("pwd"), List.of(), workspaceRoot, "working directory"),
                    monitor::isCanceled);

                var workspaceBinary = bazelWorkspace.getBazelBinary();
                if (workspaceBinary != null) {
                    executor.execute(
                        new ArbitraryCommand(
                                Path.of("echo"),
                                List.of(workspaceBinary.executable().toString()),
                                workspaceRoot,
                                "Workspace binary"),
                        monitor::isCanceled);
                    executor.execute(
                        new ArbitraryCommand(
                                workspaceBinary.executable(),
                                List.of("info"),
                                workspaceRoot,
                                "Workspace binary"),
                        monitor::isCanceled);
                }
            }
        } catch (IOException e) {
            throw new CoreException(Status.error("Error running diagnostics: " + e.getMessage(), e));
        }

        return Status.OK_STATUS;
    }
}