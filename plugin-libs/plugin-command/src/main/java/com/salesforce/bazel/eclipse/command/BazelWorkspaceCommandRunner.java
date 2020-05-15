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

package com.salesforce.bazel.eclipse.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.BazelAspectLocation;
import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.eclipse.abstractions.OutputStreamObserver;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.internal.BazelCommandExecutor;
import com.salesforce.bazel.eclipse.command.internal.BazelQueryHelper;
import com.salesforce.bazel.eclipse.command.internal.BazelVersionChecker;
import com.salesforce.bazel.eclipse.command.internal.BazelWorkspaceAspectHelper;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.logging.LoggerFacade;
import com.salesforce.bazel.eclipse.model.AspectPackageInfo;
import com.salesforce.bazel.eclipse.model.BazelBuildFile;
import com.salesforce.bazel.eclipse.model.BazelBuildError;
import com.salesforce.bazel.eclipse.model.BazelOutputParser;
import com.salesforce.bazel.eclipse.model.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.eclipse.model.BazelWorkspaceMetadataStrategy;

/**
 * An instance of the Bazel command interface for a specific workspace. Provides the API to run Bazel commands on a
 * specific workspace.
 * <p>
 * There is also an instance of this class that is not associated with a workspace (the global runner) but it is limited
 * in the commands it can run. It is intended only to run commands like the Bazel version check.
 */
public class BazelWorkspaceCommandRunner implements BazelWorkspaceMetadataStrategy {
    static final LogHelper LOG = LogHelper.log(BazelWorkspaceCommandRunner.class);;
    
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
     * <i>/private/var/tmp/_bazel_plaird/f521799c9882dcc6330b57416b13ba81/execroot/bazel_eclipse_feature/bazel-out/darwin-fastbuild/bin</i>
     * <p>
     * Determined by running this command line: <i>bazel info bazel-bin</i>
     */
    private File bazelBinDirectory;

    
    // GLOBAL CONFIG
    
    /**
     * Location of the Bazel command line executable. This is configured by the Preferences, and usually defaults to
     * /usr/local/bin/bazel but see BazelPreferenceInitializer for more details.
     */
    private static File bazelExecutable = null;

    /**
     * Shared class for low level parsing of Bazel command output
     */
    private static final BazelOutputParser outputParser = new BazelOutputParser();

    
    // COLLABORATORS 
    
    /**
     * Builder for Bazel commands, which may be a ShellCommandBuilder (for real Eclipse use) or a MockCommandBuilder 
     * (for simulations during functional tests).
     */
    private CommandBuilder commandBuilder;

    /**
     * Underlying command invoker which takes built Command objects and executes them.
     */
    private final BazelCommandExecutor bazelCommandExecutor;

    /**
     * Helper for running, collecting and caching the aspects that emit build dependency info for this workspace. 
     */
    private final BazelWorkspaceAspectHelper aspectHelper;
    
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
     * Cache of queried BUILD files
     */
    private Map<String, BazelBuildFile> buildFileCache = new TreeMap<>();
    
    /**
     * This is to cache the last query and return the query result without actually computing it. This is required 
     * because computeUnresolvedPath tries to compute the bazel query multiple time
     */
    private String query;
    private List<String> queryResults;

    
    
    // CTORS

    /**
     * This constructor creates the 'global' runner, which is a limited runner that only runs commands such as version check.
     */
    BazelWorkspaceCommandRunner(File bazelExecutable, CommandBuilder commandBuilder) {

        this.commandBuilder = commandBuilder;
        this.bazelCommandExecutor = new BazelCommandExecutor(bazelExecutable, commandBuilder);        
        this.bazelVersionChecker = new BazelVersionChecker(this.commandBuilder);
        
        // these operations are not available without a workspace, and are nulled out
        this.bazelWorkspaceRootDirectory = null;
        this.aspectHelper = null;
        this.bazelQueryHelper = null;
    }
    
    /**
     * For each Bazel workspace in the Eclipse workspace, there will be an instance of this runner.
     */
    BazelWorkspaceCommandRunner(File bazelExecutable, BazelAspectLocation aspectLocation,
            CommandBuilder commandBuilder, CommandConsoleFactory consoleFactory, File bazelWorkspaceRoot) {

        if (bazelWorkspaceRoot == null || !bazelWorkspaceRoot.exists()) {
            throw new IllegalArgumentException("Bazel workspace root directory cannot be null, and must exist.");
        }
        this.bazelWorkspaceRootDirectory = bazelWorkspaceRoot;
        this.commandBuilder = commandBuilder;
        this.bazelCommandExecutor = new BazelCommandExecutor(bazelExecutable, commandBuilder);
        
        this.aspectHelper = new BazelWorkspaceAspectHelper(this, aspectLocation, this.bazelCommandExecutor);
        this.bazelVersionChecker = new BazelVersionChecker(this.commandBuilder);
        this.bazelQueryHelper = new BazelQueryHelper(bazelCommandExecutor);
    }

    
    // WORKSPACE CONFIG
    
    /**
     * Returns the workspace root directory (where the WORKSPACE file is) for the workspace associated with this runner
     */
    public File getBazelWorkspaceRootDirectory() {
        return this.bazelWorkspaceRootDirectory;
    }
    
    /**
     * Returns the execution root of the current Bazel workspace.
     */
    public File computeBazelWorkspaceExecRoot() {

        if (bazelExecRootDirectory == null) {
            try {
                ImmutableList.Builder<String> argBuilder = ImmutableList.builder();
                argBuilder.add("info").add("execution_root");
                
                List<String> outputLines = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, null, 
                    argBuilder.build(), (t) -> t);
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
     * @param query is a String with the bazel query
     */
    @Override
    public List<String> computeBazelQuery(String query) {
    	
    	if (this.query !=null && this.query.equals(query) ) {
    		return this.queryResults;
    	}
    	
        List<String> results = new ArrayList<>();
        try {
        	ImmutableList.Builder<String> argBuilder = ImmutableList.builder();
            argBuilder.add("query").add(query);
            
            results = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, null, argBuilder.build(), (t) -> t);

        } catch (IOException | InterruptedException | BazelCommandLineToolConfigurationException e) {
            throw new IllegalStateException(e);
        }
        //update cached values
        this.query = query;
        this.queryResults = results;
        
        return results;
    }

    /**
     * Returns the output base of the current Bazel workspace.
     */
    public File computeBazelWorkspaceOutputBase() {
        if (bazelOutputBaseDirectory == null) {
            try {
                ImmutableList.Builder<String> argBuilder = ImmutableList.builder();
                argBuilder.add("info").add("output_base");
                
                List<String> outputLines = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, null, 
                    argBuilder.build(), (t) -> t);
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
    public File computeBazelWorkspaceBin() {
        if (bazelBinDirectory == null) {
            try {
                ImmutableList.Builder<String> argBuilder = ImmutableList.builder();
                argBuilder.add("info").add("bazel-bin");
                
                List<String> outputLines = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, null, 
                    argBuilder.build(), (t) -> t);
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
    public void populateBazelWorkspaceCommandOptions(BazelWorkspaceCommandOptions commandOptions) {
        try {
            ImmutableList.Builder<String> argBuilder = ImmutableList.builder();
            // to get the options, the verb could be info, build, test etc but 'test' gives us the most coverage of the contexts for options 
            argBuilder.add("test").add("--announce_rc");
            
            List<String> outputLines = bazelCommandExecutor.runBazelAndGetErrorLines(bazelWorkspaceRootDirectory, null, 
                argBuilder.build(), (t) -> t, null, null);
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
     * Get the file system path to the Bazel executable. Set by the Preferences page, defaults to /usr/local/bin/bazel
     * but see BazelPreferenceInitializer for the details of how it gets set initially.
     *
     * @return the file system path to the Bazel executable
     * @throws BazelCommandLineToolConfigurationException
     */
    public static String getBazelExecutablePath() throws BazelCommandLineToolConfigurationException {
        if (bazelExecutable == null || !bazelExecutable.exists() || !bazelExecutable.canExecute()) {
            throw new BazelCommandLineToolConfigurationException.BazelNotSetException();
        }
        return bazelExecutable.toString();
    }

    
    // OPERATIONS
    
    
    // BUILD, BUILD INFO, RUN, TEST OPERATIONS
    
    /**
     * Returns the list of targets found in the BUILD file for the given label. Uses Bazel Query to build the
     * list. This operation is cached internally, so repeated calls in the same label are cheap.
     * <p>
     * @param bazelPackageName the label path that identifies the package where the BUILD file lives (//projects/libs/foo)
     */
    public synchronized BazelBuildFile queryBazelTargetsInBuildFile(WorkProgressMonitor progressMonitor,
            String bazelPackageName) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        BazelBuildFile buildFile = buildFileCache.get(bazelPackageName);
        if (buildFile != null) {
            return buildFile;
        }
        buildFile = this.bazelQueryHelper.queryBazelTargetsInBuildFile(bazelWorkspaceRootDirectory, progressMonitor, bazelPackageName);
        buildFileCache.put(bazelPackageName, buildFile);
        return buildFile;
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
        return this.bazelQueryHelper.listBazelTargetsInBuildFiles(bazelWorkspaceRootDirectory, progressMonitor, directories);
    }
    
    /**
     * Gives a list of target completions for the given beginning string. The result is the list of possible completion
     * for a target pattern starting with string.
     * <p>
     * <b>WARNING:</b> this method was written for the original Bazel plugin for a search feature, but was not actually
     * used as far as we can tell. It may or may not work as advertised.
     *
     * @param userSearchString
     *            the partial target string entered by the user
     *
     * @throws BazelCommandLineToolConfigurationException
     */
    public List<String> getMatchingTargets(String userSearchString, WorkProgressMonitor progressMonitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        return this.bazelQueryHelper.getMatchingTargets(this.bazelWorkspaceRootDirectory, userSearchString, progressMonitor);
    }
    
    /**
     * Run a bazel build on a list of targets in the current workspace.
     *
     * @return a List of error details, this list is empty if the build was successful
     *
     * @throws InterruptedException
     * @throws IOException
     * @throws BazelCommandLineToolConfigurationException
     */
    public synchronized List<BazelBuildError> runBazelBuild(Set<String> bazelTargets,
            WorkProgressMonitor progressMonitor, List<String> extraArgs, OutputStreamObserver outputStreamObserver, OutputStreamObserver errorStreamObserver)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        List<String> extraArgsList = ImmutableList.<String> builder().add("build").addAll(this.buildOptions)
                .addAll(extraArgs).add("--").addAll(bazelTargets).build();

        List<String> output = this.bazelCommandExecutor.runBazelAndGetErrorLines(bazelWorkspaceRootDirectory, progressMonitor,
            extraArgsList, new ErrorOutputSelector(), outputStreamObserver, errorStreamObserver);
        if (output.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<BazelBuildError> errorDetails = outputParser.getErrorBazelMarkerDetails(output);
            getLogger().debug(getClass(),
                "\n" + String.join("\n", errorDetails.stream().map(d -> d.toString()).collect(Collectors.toList()))
                        + "\n");
            return errorDetails;
        }
    }
    
    // ASPECT OPERATIONS

    /**
     * Runs the analysis of the given list of targets using the build information Bazel Aspect and returns a map of
     * {@link AspectPackageInfo}-s (key is the label of the target) containing the parsed form of the JSON file created
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
    public synchronized Map<String, Set<AspectPackageInfo>> getAspectPackageInfos(String eclipseProjectName,
            Collection<String> targetLabels, WorkProgressMonitor progressMonitor, String caller)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        return this.aspectHelper.getAspectPackageInfos(eclipseProjectName, targetLabels, progressMonitor, caller);
    }

    /**
     * Runs the analysis of the given list of targets using the build information Bazel Aspect and returns a map of
     * {@link AspectPackageInfo}-s (key is the label of the target) containing the parsed form of the JSON file created
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
    public synchronized Set<AspectPackageInfo> getAspectPackageInfos(String eclipseProjectName,
            String targetLabel, WorkProgressMonitor progressMonitor, String caller)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        Set<String> targetLabels = new TreeSet<>();
        targetLabels.add(targetLabel);
        Map<String, Set<AspectPackageInfo>> results = this.aspectHelper.getAspectPackageInfos(eclipseProjectName, targetLabels, progressMonitor, caller);
        
        Set<AspectPackageInfo> resultSet = results.get(targetLabel);
        if (resultSet == null) {
            // this is a non-Java target, just return an empty set
            resultSet = new TreeSet<>();
        }
        return resultSet;
    }
    
    /**
     * Clear the entire AspectPackageInfo cache. This flushes the dependency graph for the workspace.
     */
    public synchronized void flushAspectInfoCache() {
        this.aspectHelper.flushAspectInfoCache();
    }

    /**
     * Clear the AspectPackageInfo cache for the passed target. This flushes the dependency graph for that target.
     */
    public synchronized void flushAspectInfoCache(String target) {
        this.aspectHelper.flushAspectInfoCache(target);
    }
    
    /**
     * Clear the AspectPackageInfo cache for the passed targets. This flushes the dependency graph for those targets.
     */
    public synchronized void flushAspectInfoCache(Set<String> targets) {
        this.aspectHelper.flushAspectInfoCache(targets);
    }
    
    /**
     * Access to the low level aspect collaborator. Visible for tests.
     */
    public BazelWorkspaceAspectHelper getBazelWorkspaceAspectHelper() {
        return this.aspectHelper;
    }
    
    
    // CUSTOM OPERATIONS
    
    /**
     * Returns a builder for issuing custom commands that are not covered in the convenience APIs in this class. 
     * You can use the CommandBuilder to build any command you need. For 'run' or 'test' commands, consider using 
     * the special purpose BazelLauncherBuilder instead.
     */
    public CommandBuilder getBazelCommandBuilder() {
        return this.commandBuilder;
    }
    
    /**
     * Returns a builder for issuing custom launcher commands (e.g. 'bazel run', 'bazel test'). The builder
     * comes pre-wired into other collaborators. 
     */
    public BazelLauncherBuilder getBazelLauncherBuilder() {
        BazelLauncherBuilder launcherBuilder = new BazelLauncherBuilder(this, this.commandBuilder);
        
        return launcherBuilder;
    }

    
    // SPECIAL OPERATIONS
    

    /**
     * Runs the clean command on the workspace.
     */
    public void runBazelClean(WorkProgressMonitor progressMonitor) {
        try {
            ImmutableList.Builder<String> argBuilder = ImmutableList.builder();
            argBuilder.add("clean");

            bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, progressMonitor, 
                argBuilder.build(), (t) -> t);
        } catch (IOException | InterruptedException | BazelCommandLineToolConfigurationException e) {
            e.printStackTrace();
        }
    }
        
    /**
     * Checks the version of the bazel binary configured at the path specified in the Preferences.
     *
     * @throws BazelCommandLineToolConfigurationException
     */
    public void runBazelVersionCheck() throws BazelCommandLineToolConfigurationException {
        bazelVersionChecker.runBazelVersionCheck(bazelExecutable, this.bazelWorkspaceRootDirectory);
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
            ioe.printStackTrace();
        }
        return directory;
    }

    private LoggerFacade getLogger() {
        return LoggerFacade.instance();
    }

}
