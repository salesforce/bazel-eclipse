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

package com.salesforce.bazel.sdk.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.internal.BazelCommandExecutor;
import com.salesforce.bazel.sdk.command.internal.BazelQueryHelper;
import com.salesforce.bazel.sdk.command.internal.BazelVersionChecker;
import com.salesforce.bazel.sdk.command.internal.BazelWorkspaceAspectProcessor;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.logging.LoggerFacade;
import com.salesforce.bazel.sdk.model.BazelBuildFile;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceMetadataStrategy;

/**
 * An instance of the Bazel command interface for a specific workspace. Provides the API to run Bazel commands on a
 * specific workspace.
 * <p>
 * There is also an instance of this class that is not associated with a workspace (the global runner) but it is limited
 * in the commands it can run. It is intended only to run commands like the Bazel version check.
 */
public class BazelWorkspaceCommandRunner implements BazelWorkspaceMetadataStrategy {
    static final LogHelper LOG = LogHelper.log(BazelWorkspaceCommandRunner.class);

    // WORKSPACE CONFIG

    /**
     * The location on disk for the workspace.
     */
    private final File bazelWorkspaceRootDirectory;

    /**
     * The internal location on disk for Bazel's 'execroot' for this workspace. E.g.
     * <i>/private/var/tmp/_bazel_plaird/edb34c7f4bfffeb66012c4fc6aaab239/execroot/bazel_demo_simplejava</i>
     * <p>
     * Determined by running this command line: <i>bazel info execution_root</i>
     */
    private File bazelExecRootDirectory;

    /**
     * The internal location on disk for Bazel's 'output base' for this workspace. E.g.
     * <i>/private/var/tmp/_bazel_plaird/edb34c7f4bfffeb66012c4fc6aaab239</i>
     * <p>
     * Determined by running this command line: <i>bazel info output_base</i>
     */
    private File bazelOutputBaseDirectory;

    /**
     * The internal location on disk for Bazel's 'bazel-bin' for this workspace. E.g.
     * <i>/private/var/tmp/_bazel_plaird/f521799c9882dcc6330b57416b13ba81/execroot/bazel_feature/bazel-out/darwin-fastbuild/bin</i>
     * <p>
     * Determined by running this command line: <i>bazel info bazel-bin</i>
     */
    private File bazelBinDirectory;

    // GLOBAL CONFIG

    /**
     * Location of the Bazel command line executable.
     */
    private static File bazelExecutable = null;

    // COLLABORATORS

    /**
     * Builder for Bazel commands, which may be a ShellCommandBuilder (for real IDE use) or a MockCommandBuilder (for
     * simulations during functional tests).
     */
    private final CommandBuilder commandBuilder;

    /**
     * Underlying command invoker which takes built Command objects and executes them.
     */
    private final BazelCommandExecutor bazelCommandExecutor;

    /**
     * Helper for running, collecting and caching the aspects that emit build dependency info for this workspace.
     */
    private final BazelWorkspaceAspectProcessor aspectHelper;

    /**
     * Helper for running bazel query commands.
     */
    private final BazelQueryHelper bazelQueryHelper;

    /**
     * Helper for running version checks of the configured Bazel executable.
     */
    private final BazelVersionChecker bazelVersionChecker;

    /**
     * These arguments are added to all "bazel build" commands that run for the purpose of building code. These may be
     * workspace specific.
     */
    private List<String> buildOptions = Collections.emptyList();

    // CACHES

    /**
     * This is to cache the last query and return the query result without actually computing it. This is required
     * because computeUnresolvedPath tries to compute the bazel query multiple time
     */
    private String query;
    private List<String> queryResults;

    // CTORS

    /**
     * This constructor creates the 'global' runner, which is a limited runner that only runs commands such as version
     * check.
     */
    public BazelWorkspaceCommandRunner(File bazelExecutable, CommandBuilder commandBuilder) {

        this.commandBuilder = commandBuilder;
        bazelCommandExecutor = new BazelCommandExecutor(bazelExecutable, commandBuilder);
        bazelVersionChecker = new BazelVersionChecker(this.commandBuilder);

        // these operations are not available without a workspace, and are nulled out
        bazelWorkspaceRootDirectory = null;
        aspectHelper = null;
        bazelQueryHelper = null;
    }

    /**
     * For each Bazel workspace, there will be an instance of this runner.
     */
    public BazelWorkspaceCommandRunner(File bazelExecutable, BazelAspectLocation aspectLocation,
            CommandBuilder commandBuilder, CommandConsoleFactory consoleFactory, File bazelWorkspaceRoot) {

        if ((bazelWorkspaceRoot == null) || !bazelWorkspaceRoot.exists()) {
            throw new IllegalArgumentException("Bazel workspace root directory cannot be null, and must exist.");
        }
        bazelWorkspaceRootDirectory = bazelWorkspaceRoot;
        this.commandBuilder = commandBuilder;
        bazelCommandExecutor = new BazelCommandExecutor(bazelExecutable, commandBuilder);

        aspectHelper = new BazelWorkspaceAspectProcessor(this, aspectLocation, bazelCommandExecutor);
        bazelVersionChecker = new BazelVersionChecker(this.commandBuilder);
        bazelQueryHelper = new BazelQueryHelper(bazelCommandExecutor);
    }

    // WORKSPACE CONFIG

    /**
     * Returns the workspace root directory (where the WORKSPACE file is) for the workspace associated with this runner
     */
    public File getBazelWorkspaceRootDirectory() {
        return bazelWorkspaceRootDirectory;
    }

    /**
     * Returns the execution root of the current Bazel workspace.
     */
    @Override
    public File computeBazelWorkspaceExecRoot() {

        if (bazelExecRootDirectory == null) {
            try {
                List<String> argBuilder = new ArrayList<>();
                argBuilder.add("info");
                argBuilder.add("execution_root");

                List<String> outputLines = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory,
                    null, argBuilder, t -> t, BazelCommandExecutor.TIMEOUT_INFINITE);
                outputLines = BazelCommandExecutor.stripInfoLines(outputLines);
                bazelExecRootDirectory = new File(String.join("", outputLines));
                bazelExecRootDirectory = getCanonicalFileSafely(bazelExecRootDirectory);
            } catch (Exception anyE) {
                throw new IllegalStateException(anyE);
            }
        }
        return bazelExecRootDirectory;
    }

    /**
     * Returns the list of targets for the given bazel query
     *
     * @param query
     *            is a String with the bazel query
     */
    @Override
    public List<String> computeBazelQuery(String query) {

        if ((this.query != null) && this.query.equals(query)) {
            return queryResults;
        }

        List<String> results = new ArrayList<>();
        try {
            List<String> argBuilder = new ArrayList<>();
            argBuilder.add("query");
            argBuilder.add(query);

            results = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, null, argBuilder,
                t -> t, BazelCommandExecutor.TIMEOUT_INFINITE);

        } catch (IOException | InterruptedException | BazelCommandLineToolConfigurationException e) {
            throw new IllegalStateException(e);
        }
        //update cached values
        this.query = query;
        queryResults = results;

        return results;
    }

    /**
     * Returns the output base of the current Bazel workspace.
     */
    @Override
    public File computeBazelWorkspaceOutputBase() {
        if (bazelOutputBaseDirectory == null) {
            try {
                List<String> argBuilder = new ArrayList<>();
                argBuilder.add("info");
                argBuilder.add("output_base");

                List<String> outputLines = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory,
                    null, argBuilder, t -> t, BazelCommandExecutor.TIMEOUT_INFINITE);
                outputLines = BazelCommandExecutor.stripInfoLines(outputLines);
                bazelOutputBaseDirectory = new File(String.join("", outputLines));
                bazelOutputBaseDirectory = getCanonicalFileSafely(bazelOutputBaseDirectory);
            } catch (Exception anyE) {
                throw new IllegalStateException(anyE);
            }
        }
        return bazelOutputBaseDirectory;
    }

    /**
     * Returns the bazel-bin of the current Bazel workspace.
     */
    @Override
    public File computeBazelWorkspaceBin() {
        if (bazelBinDirectory == null) {
            try {
                List<String> argBuilder = new ArrayList<>();
                argBuilder.add("info");
                argBuilder.add("bazel-bin");

                List<String> outputLines = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory,
                    null, argBuilder, t -> t, BazelCommandExecutor.TIMEOUT_INFINITE);
                outputLines = BazelCommandExecutor.stripInfoLines(outputLines);
                bazelBinDirectory = new File(String.join("", outputLines));
                bazelBinDirectory = getCanonicalFileSafely(bazelBinDirectory);
            } catch (Exception anyE) {
                throw new IllegalStateException(anyE);
            }
        }
        return bazelBinDirectory;
    }

    /**
     * Returns the explicitly set options in the workspace config files (.bazelrc et al)
     */
    @Override
    public void populateBazelWorkspaceCommandOptions(BazelWorkspaceCommandOptions commandOptions) {
        try {
            List<String> argBuilder = new ArrayList<>();
            // to get the options, the verb could be info, build, test etc but 'test' gives us the most coverage of the contexts for options
            argBuilder.add("test");
            argBuilder.add("--announce_rc");

            List<String> outputLines = bazelCommandExecutor.runBazelAndGetErrorLines(bazelWorkspaceRootDirectory, null,
                argBuilder, t -> t, BazelCommandExecutor.TIMEOUT_INFINITE);
            commandOptions.parseOptionsFromOutput(outputLines);
        } catch (Exception anyE) {
            throw new IllegalStateException(anyE);
        }
    }

    /**
     * These arguments are added to all "bazel build" commands that run for the purpose of building code.
     */
    public void setBuildOptions(List<String> buildOptions) {
        this.buildOptions = buildOptions;
    }

    // GLOBAL CONFIG

    /**
     * Set the path to the Bazel binary. Allows the user to override the default via the Preferences ui.
     */
    public synchronized static void setBazelExecutablePath(String bazelExecutablePath) {
        bazelExecutable = new File(bazelExecutablePath);
    }

    /**
     * Get the file system path to the Bazel executable.
     *
     * @return the file system path to the Bazel executable
     * @throws BazelCommandLineToolConfigurationException
     */
    public static String getBazelExecutablePath() throws BazelCommandLineToolConfigurationException {
        if ((bazelExecutable == null) || !bazelExecutable.exists() || !bazelExecutable.canExecute()) {
            // TODO move this to the setter paths
            throw new BazelCommandLineToolConfigurationException.BazelNotSetException();
        }
        return bazelExecutable.toString();
    }

    // OPERATIONS

    // BUILD, BUILD INFO, RUN, TEST OPERATIONS

    /**
     * Returns the list of targets found in the BUILD file for the given label. Uses Bazel Query to build the list. This
     * operation is cached internally, so repeated calls in the same label are cheap.
     * <p>
     *
     * @param labels
     *            the labels to query
     */
    public synchronized Collection<BazelBuildFile> queryBazelTargetsInBuildFile(Collection<BazelLabel> labels)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        return bazelQueryHelper.queryBazelTargetsInBuildFile(bazelWorkspaceRootDirectory, labels);
    }

    /**
     * @param bazelPackageName
     *            the label path that identifies the package where the BUILD file lives (//projects/libs/foo)
     */
    public synchronized void flushQueryCache(BazelLabel bazelPackageLabel) {
        bazelQueryHelper.flushCache(bazelPackageLabel);
    }

    /**
     * Returns the list of targets found in the BUILD files for the given sub-directories. Uses Bazel Query to build the
     * list.
     *
     * @param progressMonitor
     *            can be null
     * @throws BazelCommandLineToolConfigurationException
     */
    @Deprecated
    public synchronized List<String> listBazelTargetsInBuildFiles(WorkProgressMonitor progressMonitor,
            File... directories) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        return bazelQueryHelper.listBazelTargetsInBuildFiles(bazelWorkspaceRootDirectory, progressMonitor, directories);
    }

    /**
     * Run a bazel build on a list of targets in the current workspace.
     *
     * @return a List of error details, this list is empty if the build was successful
     */
    public synchronized List<BazelProblem> runBazelBuild(Set<String> bazelTargets, List<String> extraArgs)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        return runBazelBuild(bazelTargets, extraArgs, null);
    }

    /**
     * Run a bazel build on a list of targets in the current workspace.
     *
     * @return a List of error details, this list is empty if the build was successful
     */
    public synchronized List<BazelProblem> runBazelBuild(Set<String> bazelTargets, List<String> extraArgs,
            WorkProgressMonitor progressMonitor)
                    throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        List<String> extraArgsList = new ArrayList<String>();
        extraArgsList.add("build");
        extraArgsList.addAll(buildOptions);
        extraArgsList.addAll(extraArgs);
        extraArgsList.add("--");
        extraArgsList.addAll(bazelTargets);

        List<String> output = bazelCommandExecutor.runBazelAndGetErrorLines(bazelWorkspaceRootDirectory,
            progressMonitor, extraArgsList, new ErrorOutputSelector(), BazelCommandExecutor.TIMEOUT_INFINITE);
        if (output.isEmpty()) {
            return Collections.emptyList();
        } else {
            BazelOutputParser outputParser = new BazelOutputParser();
            List<BazelProblem> errors = outputParser.getErrors(output);
            logErrors(errors);
            return errors;
        }
    }

    private void logErrors(List<BazelProblem> errors) {
        List<String> errorStrs = errors.stream().map(BazelProblem::toString).collect(Collectors.toList());
        getLogger().debug(getClass(), "\n" + String.join("\n", errorStrs) + "\n");
    }

    // ASPECT OPERATIONS

    /**
     * Runs the analysis of the given list of targets using the build information Bazel Aspect and returns a map of
     * {@link AspectTargetInfo}-s (key is the label of the target) containing the parsed form of the JSON file created
     * by the aspect.
     * <p>
     * This method caches its results and won't recompute a previously computed version unless
     * {@link #flushAspectInfoCache()} has been called in between.
     * <p>
     * TODO it would be worthwhile to evaluate whether Aspects are the best way to get build info, as we could otherwise
     * use Bazel Query here as well.
     *
     * @throws BazelCommandLineToolConfigurationException
     */
    public synchronized Map<BazelLabel, Set<AspectTargetInfo>> getAspectTargetInfoForPackages(
            Collection<BazelPackageLocation> targetPackages, String caller)
                    throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        List<BazelLabel> targetLabels = new ArrayList<>();
        for (BazelPackageLocation pkg : targetPackages) {
            String target = pkg.getBazelPackageFSRelativePath() + ":*";
            targetLabels.add(new BazelLabel(target));
        }

        return aspectHelper.getAspectTargetInfos(targetLabels, caller);
    }

    /**
     * Runs the analysis of the given list of targets using the build information Bazel Aspect and returns a map of
     * {@link AspectTargetInfo}-s (key is the label of the target) containing the parsed form of the JSON file created
     * by the aspect.
     * <p>
     * This method caches its results and won't recompute a previously computed version unless
     * {@link #flushAspectInfoCache()} has been called in between.
     * <p>
     * TODO it would be worthwhile to evaluate whether Aspects are the best way to get build info, as we could otherwise
     * use Bazel Query here as well.
     *
     * @return Mapping of the requested label to its AspectTargetInfo instances
     * @throws BazelCommandLineToolConfigurationException
     */
    public synchronized Map<BazelLabel, Set<AspectTargetInfo>> getAspectTargetInfos(Collection<String> targetLabels,
            String caller) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        Collection<BazelLabel> labels = targetLabels.stream().map(BazelLabel::new).collect(Collectors.toList());
        return aspectHelper.getAspectTargetInfos(labels, caller);
    }

    /**
     * Clear the entire AspectTargetInfo cache. This flushes the dependency graph for the workspace.
     */
    public synchronized void flushAspectInfoCache() {
        aspectHelper.flushAspectInfoCache();
    }

    /**
     * Clear the AspectTargetInfo cache for the passed target. This flushes the dependency graph for that target.
     */
    public synchronized void flushAspectInfoCache(String target) {
        aspectHelper.flushAspectInfoCache(new BazelLabel(target));
    }

    /**
     * Clear the AspectTargetInfo cache for the passed targets. This flushes the dependency graph for those targets.
     */
    public synchronized void flushAspectInfoCache(Set<String> targets) {
        Set<BazelLabel> labels = targets.stream().map(BazelLabel::new).collect(Collectors.toSet());
        aspectHelper.flushAspectInfoCache(labels);
    }

    /**
     * Clear the AspectTargetInfo cache for the passed package. This flushes the dependency graph for any target that
     * contains the package name.
     */
    public synchronized Set<String> flushAspectInfoCacheForPackage(String packageName) {
        BazelLabel packageLabel = new BazelLabel(packageName);
        Set<BazelLabel> flushedPackages = aspectHelper.flushAspectInfoCacheForPackage(packageLabel);
        LOG.info("Flushed aspect cache for package: " + packageLabel);
        return flushedPackages.stream().map(BazelLabel::getPackagePath).collect(Collectors.toSet());
    }

    /**
     * Access to the low level aspect collaborator. Visible for tests.
     */
    public BazelWorkspaceAspectProcessor getBazelWorkspaceAspectHelper() {
        return aspectHelper;
    }

    // CUSTOM OPERATIONS

    /**
     * Returns a builder for issuing custom commands that are not covered in the convenience APIs in this class. You can
     * use the CommandBuilder to build any command you need. For 'run' or 'test' commands, consider using the special
     * purpose BazelLauncherBuilder instead.
     */
    public CommandBuilder getBazelCommandBuilder() {
        return commandBuilder;
    }

    /**
     * Returns a builder for issuing custom launcher commands (e.g. 'bazel run', 'bazel test'). The builder comes
     * pre-wired into other collaborators.
     */
    public BazelLauncherBuilder getBazelLauncherBuilder() {
        BazelLauncherBuilder launcherBuilder = new BazelLauncherBuilder(this, commandBuilder);

        return launcherBuilder;
    }

    // SPECIAL OPERATIONS

    /**
     * Runs the clean command on the workspace.
     */
    public void runBazelClean(WorkProgressMonitor progressMonitor) {
        try {
            List<String> argBuilder = new ArrayList<>();
            argBuilder.add("clean");

            bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, progressMonitor, argBuilder,
                t -> t, BazelCommandExecutor.TIMEOUT_INFINITE);
        } catch (IOException | InterruptedException | BazelCommandLineToolConfigurationException e) {
            LOG.error("Exception running Bazel clean.", e);
        }
    }

    /**
     * Checks the version of the bazel binary configured at the path specified in the Preferences.
     *
     * @throws BazelCommandLineToolConfigurationException
     */
    public void runBazelVersionCheck() throws BazelCommandLineToolConfigurationException {
        bazelVersionChecker.runBazelVersionCheck(bazelExecutable, bazelWorkspaceRootDirectory);
    }

    // HELPERS

    private static class ErrorOutputSelector implements Function<String, String> {

        private boolean keep = false;

        @Override
        public String apply(String line) {
            if (line.startsWith("ERROR")) {
                keep = true;
            }
            return keep ? line : null;
        }
    }

    /**
     * Resolve softlinks and other abstractions in the workspace paths.
     */
    private File getCanonicalFileSafely(File directory) {
        if (directory == null) {
            return null;
        }
        try {
            directory = directory.getCanonicalFile();
        } catch (IOException ioe) {
            LOG.error("Error locating path on file system: [{}]", ioe, directory.getAbsolutePath());
        }
        return directory;
    }

    private LoggerFacade getLogger() {
        return LoggerFacade.instance();
    }

}
