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
 */
package com.salesforce.bazel.eclipse.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.internal.ConsoleType;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.TargetKind;

/**
 * Convenience class that manufactures Bazel Command instances used for Bazel 'run' or 'test' commands 
 * (e.g. for Eclipse Launch Configs).
 */
public class BazelLauncherBuilder {

    private final BazelWorkspaceCommandRunner bazelCommandRunner;
    private final CommandBuilder commandBuilder;
    
    private BazelLabel bazelLabel;
    private TargetKind targetKind;
    private Map<String, String> bazelArgs;

    private boolean isDebugMode;
    private String debugHost;
    private int debugPort;

    // CTORS
    // Get instances via BazelWorkspaceCommandRunner.getBazelLauncherBuilder()
    
    BazelLauncherBuilder(BazelWorkspaceCommandRunner bazelRunner, CommandBuilder commandBuilder) {
        this.bazelCommandRunner = Objects.requireNonNull(bazelRunner);
        this.commandBuilder = Objects.requireNonNull(commandBuilder);
    }

    BazelLauncherBuilder(BazelWorkspaceCommandRunner bazelRunner, CommandBuilder commandBuilder,
        BazelLabel bazelLabel, TargetKind targetKind, Map<String, String> bazelArgs) {
        this.bazelCommandRunner = Objects.requireNonNull(bazelRunner);
        this.commandBuilder = Objects.requireNonNull(commandBuilder);
        this.bazelLabel = Objects.requireNonNull(bazelLabel);
        this.targetKind = Objects.requireNonNull(targetKind);
        this.bazelArgs = Objects.requireNonNull(bazelArgs);
    }


    // SETTERS
    
    public BazelLauncherBuilder setLabel(BazelLabel bazelLabel) {
         this.bazelLabel = bazelLabel;
         return this;
    }

    public BazelLauncherBuilder setTargetKind(TargetKind targetKind) {
        this.targetKind = targetKind;
        return this;
   }

    public BazelLauncherBuilder setArgs(Map<String, String> bazelArgs) {
        this.bazelArgs = bazelArgs;
        return this;
   }

    public BazelLauncherBuilder setDebugMode(boolean isDebugMode, String debugHost, int debugPort) {
        this.isDebugMode = isDebugMode;
        this.debugHost = debugHost;
        this.debugPort = debugPort;
        return this;
    }


    // BUILD
    
    public Command build() {
        Objects.requireNonNull(bazelCommandRunner);
        Objects.requireNonNull(bazelLabel);
        Objects.requireNonNull(targetKind);
        Objects.requireNonNull(bazelArgs);
        
        List<String> args = new ArrayList<>();
        if (isDebugMode) {
            if (targetKind.isTestable()) {
                args.add("--test_arg=--wrapper_script_flag=--debug=" + debugHost + ":" + debugPort);
            } else {
                args.add(String.format("--jvmopt='-agentlib:jdwp=transport=dt_socket,address=%s:%s,server=y,suspend=y'",
                    debugHost, debugPort));
            }
        }

        for (Map.Entry<String, String> arg : bazelArgs.entrySet()) {
            args.add(arg.getKey() + "=" + arg.getValue());
        }

        try {
            return targetKind.isTestable()
                    ? getBazelTestCommand(Collections.singletonList(bazelLabel.toString()), args)
                    : getBazelRunCommand(Collections.singletonList(bazelLabel.toString()), args);
        } catch (IOException | BazelCommandLineToolConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
    /**
     * Builds and returns a Command instance representing a "bazel run" invocation.
     *
     * @return Command instance
     */
    private Command getBazelRunCommand(List<String> bazelTargets, List<String> extraArgs)
            throws IOException, BazelCommandLineToolConfigurationException {
        List<String> args = ImmutableList.<String> builder().add("run").addAll(extraArgs)
                .add("--").addAll(bazelTargets).build();

        WorkProgressMonitor progressMonitor = null;
        
        File workspaceDirectory = this.bazelCommandRunner.getBazelWorkspaceRootDirectory();
        String consoleName = ConsoleType.WORKSPACE.getConsoleName(workspaceDirectory);
        
        return commandBuilder
                .setConsoleName(consoleName)
                .setDirectory(workspaceDirectory)
                .addArguments(BazelWorkspaceCommandRunner.getBazelExecutablePath())
                .addArguments(args)
                .setProgressMonitor(progressMonitor)
                .build();
    }

    /**
     * Builds and returns a Command instance representing a "bazel test" invocation.
     *
     * @return Command instance
     */
    private Command getBazelTestCommand(List<String> bazelTargets, List<String> extraArgs)
            throws IOException, BazelCommandLineToolConfigurationException {

        // need to add single method support:
        // --test_filter=com.blah.foo.hello.HelloAgain2Test#testHelloAgain2$

        List<String> args = ImmutableList.<String> builder().add("test")
                .add("--test_output=streamed").add("--test_strategy=exclusive").add("--test_timeout=9999")
                .add("--nocache_test_results").add("--runs_per_test=1").add("--flaky_test_attempts=1").addAll(extraArgs)
                .add("--").addAll(bazelTargets).build();

        WorkProgressMonitor progressMonitor = null;
        
        
        File workspaceDirectory = this.bazelCommandRunner.getBazelWorkspaceRootDirectory();
        String consoleName = ConsoleType.WORKSPACE.getConsoleName(workspaceDirectory);
        
        return commandBuilder
                .setConsoleName(consoleName)
                .setDirectory(workspaceDirectory)
                .addArguments(BazelWorkspaceCommandRunner.getBazelExecutablePath())
                .addArguments(args)
                .setProgressMonitor(progressMonitor)
                .build();
    }
    
}
