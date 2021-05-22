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
package com.salesforce.bazel.sdk.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.salesforce.bazel.sdk.command.internal.ConsoleType;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelTargetKind;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Convenience class that manufactures Bazel Command instances used for Bazel 'run' or 'test' commands (e.g. for Eclipse
 * Launch Configs).
 */
public class BazelLauncherBuilder {

    private final BazelWorkspaceCommandRunner bazelCommandRunner;
    private final CommandBuilder commandBuilder;
    private final BazelOutputDirectoryBuilder outputDirectoryBuilder;

    private BazelLabel bazelLabel;
    private BazelTargetKind targetKind;
    private List<String> bazelArgs;

    private boolean isDebugMode;
    private String debugHost;
    private int debugPort;

    // CTORS
    // Get instances via BazelWorkspaceCommandRunner.getBazelLauncherBuilder()

    BazelLauncherBuilder(BazelWorkspaceCommandRunner bazelRunner, CommandBuilder commandBuilder) {
        this(bazelRunner, commandBuilder, new BazelOutputDirectoryBuilder());
    }

    BazelLauncherBuilder(BazelWorkspaceCommandRunner bazelRunner, CommandBuilder commandBuilder,
        BazelOutputDirectoryBuilder outputDirectoryBuilder) {
        bazelCommandRunner = Objects.requireNonNull(bazelRunner);
        this.commandBuilder = Objects.requireNonNull(commandBuilder);
        this.outputDirectoryBuilder = Objects.requireNonNull(outputDirectoryBuilder);
    }

    BazelLauncherBuilder(BazelWorkspaceCommandRunner bazelRunner, CommandBuilder commandBuilder, BazelLabel bazelLabel,
        BazelTargetKind targetKind, List<String> bazelArgs) {
        this(bazelRunner, commandBuilder, bazelLabel, targetKind, bazelArgs, new BazelOutputDirectoryBuilder());
    }

    BazelLauncherBuilder(BazelWorkspaceCommandRunner bazelRunner, CommandBuilder commandBuilder, BazelLabel bazelLabel,
        BazelTargetKind targetKind, List<String> bazelArgs, BazelOutputDirectoryBuilder outputDirectoryBuilder) {
        bazelCommandRunner = Objects.requireNonNull(bazelRunner);
        this.commandBuilder = Objects.requireNonNull(commandBuilder);
        this.bazelLabel = Objects.requireNonNull(bazelLabel);
        this.targetKind = Objects.requireNonNull(targetKind);
        this.bazelArgs = Objects.requireNonNull(bazelArgs);
        this.outputDirectoryBuilder = Objects.requireNonNull(outputDirectoryBuilder);
    }

    // SETTERS

    public BazelLauncherBuilder setLabel(BazelLabel bazelLabel) {
        this.bazelLabel = bazelLabel;
        return this;
    }

    public BazelLauncherBuilder setTargetKind(BazelTargetKind targetKind) {
        this.targetKind = targetKind;
        return this;
    }

    public BazelLauncherBuilder setArgs(List<String> bazelArgs) {
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

        try {
            return targetKind.isTestable() ? getBazelTestCommand(bazelLabel, isDebugMode, bazelArgs)
                    : getBazelRunCommand(bazelLabel, isDebugMode, bazelArgs);
        } catch (IOException | BazelCommandLineToolConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Builds and returns a Command instance representing a "bazel run" invocation.
     *
     * @return Command instance
     */
    private Command getBazelRunCommand(BazelLabel bazelTarget, boolean isDebugMode, List<String> extraArgs)
            throws IOException, BazelCommandLineToolConfigurationException {

        File workspaceDirectory = bazelCommandRunner.getBazelWorkspaceRootDirectory();

        // Unixy Platforms:
        // Instead of calling bazel run, we directly call the shell script that bazel run
        // would call, thereby avoiding the problem of launching 2 processes (Bazel + the process we
        // actually care about - see https://github.com/salesforce/bazel-eclipse/issues/94)
        // Windows Platforms:
        // Bazel builds an .exe file to run.
        String appPath = outputDirectoryBuilder.getRunScriptPath(bazelTarget);
        if (System.getProperty("os.name").contains("Win")) {
            appPath = appPath + ".exe";
        }
        File appFile = new File(workspaceDirectory, appPath);
        if (!appFile.exists()) {
            System.out.println("ERROR: Launch executable does not exist: " + appFile.getAbsolutePath());
        } else {
            System.out.println("Launch executable: " + appFile.getAbsolutePath());
        }

        List<String> args = new ArrayList<>();
        args.add(appFile.getAbsolutePath());

        if (isDebugMode) {
            args.add("--debug=" + debugPort);
        }

        args.addAll(extraArgs);

        WorkProgressMonitor progressMonitor = null;

        String consoleName = ConsoleType.WORKSPACE.getConsoleName(workspaceDirectory);

        return commandBuilder.setConsoleName(consoleName).setDirectory(workspaceDirectory)
                .addArguments(Collections.unmodifiableList(args)).setProgressMonitor(progressMonitor).build();
    }

    /**
     * Builds and returns a Command instance representing a "bazel test" invocation.
     *
     * @return Command instance
     */
    private Command getBazelTestCommand(BazelLabel bazelTarget, boolean isDebugMode, List<String> extraArgs)
            throws IOException, BazelCommandLineToolConfigurationException {

        // need to add single method support:
        // --test_filter=com.blah.foo.hello.HelloAgain2Test#testHelloAgain2$
        List<String> args = new ArrayList<>();
        args.add("test");
        args.add("--test_output=streamed");
        args.add("--test_strategy=exclusive");
        args.add("--test_timeout=9999");
        args.add("--nocache_test_results");
        args.add("--runs_per_test=1");
        args.add("--flaky_test_attempts=1");
        args.addAll(extraArgs);
        args.add("--");
        args.add(bazelTarget.getLabel());

        if (isDebugMode) {
            args.add("--test_arg=--wrapper_script_flag=--debug=" + debugHost + ":" + debugPort);
        }

        WorkProgressMonitor progressMonitor = null;

        File workspaceDirectory = bazelCommandRunner.getBazelWorkspaceRootDirectory();
        String consoleName = ConsoleType.WORKSPACE.getConsoleName(workspaceDirectory);

        return commandBuilder.setConsoleName(consoleName).setDirectory(workspaceDirectory)
                .addArguments(BazelWorkspaceCommandRunner.getBazelExecutablePath())
                .addArguments(Collections.unmodifiableList(args)).setProgressMonitor(progressMonitor).build();
    }

}
