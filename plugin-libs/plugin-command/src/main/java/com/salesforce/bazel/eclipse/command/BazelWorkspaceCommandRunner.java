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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.salesforce.bazel.eclipse.abstractions.BazelAspectLocation;
import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.BazelCommandRunner.ConsoleType;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.logging.LoggerFacade;
import com.salesforce.bazel.eclipse.model.AspectPackageInfo;
import com.salesforce.bazel.eclipse.model.BazelMarkerDetails;
import com.salesforce.bazel.eclipse.model.BazelOutputParser;

/**
 * An instance of the Bazel command interface for a specific workspace. Provides the API to run Bazel commands on a
 * specific workspace.
 */
public class BazelWorkspaceCommandRunner {
    static final LogHelper LOG = LogHelper.log(BazelWorkspaceCommandRunner.class);;

    private static final BazelOutputParser outputParser = new BazelOutputParser();

    /**
     * Underlying command runner. Technically, we could have a single command runner for all Bazel workspaces open in
     * the Eclipse workspace, but it may be helpful to have an instance dedicated for each.
     */
    private final BazelCommandRunner bazelCommandRunner;

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

    /**
     * These arguments are added to all "bazel build" commands that run for the purpose of building code.
     */
    private final List<String> buildOptions;

    /**
     * These arguments are added to all "bazel build" commands that run for aspect processing
     */
    private final List<String> aspectOptions;

    /**
     * Cache of the Aspect data for each target. key=String target (//a/b/c) value=AspectPackageInfo data that came from
     * running the aspect. This cache is cleared often (currently, every build, but that is too often)
     */
    private final Map<String, AspectPackageInfo> aspectInfoCache_current = new HashMap<>();

    /**
     * Cache of the Aspect data for each target. key=String target (//a/b/c) value=AspectPackageInfo data that came from
     * running the aspect. This cache is never cleared and is used for cases in which the developer introduces a compile
     * error into the package, such that the Aspect will fail to run.
     */
    private final Map<String, AspectPackageInfo> aspectInfoCache_lastgood = new HashMap<>();

    BazelWorkspaceCommandRunner(BazelCommandFacade bazelCommandFacade, BazelAspectLocation aspectLocation,
            CommandBuilder commandBuilder, CommandConsoleFactory consoleFactory, File bazelWorkspaceRoot) {

        if (bazelWorkspaceRoot == null || !bazelWorkspaceRoot.exists()) {
            throw new IllegalArgumentException("Bazel workspace root directory cannot be null, and must exist.");
        }
        this.bazelWorkspaceRootDirectory = bazelWorkspaceRoot;
        this.bazelCommandRunner = new BazelCommandRunner(bazelCommandFacade, commandBuilder);
        this.buildOptions = Collections.emptyList();
        this.aspectOptions = ImmutableList.<String> builder()
                .add("--override_repository=local_eclipse_aspect=" + aspectLocation.getAspectDirectory(),
                    "--aspects=@local_eclipse_aspect" + aspectLocation.getAspectLabel(), "-k",
                    "--output_groups=ide-info-text,ide-resolve,-_,-defaults", "--experimental_show_artifacts")
                .build();
    }

    /**
     * Returns the execution root of the current Bazel workspace.
     *
     * @param progressMonitor
     *            can be null
     */
    public File getBazelWorkspaceExecRoot(WorkProgressMonitor progressMonitor) {

        if (bazelExecRootDirectory == null) {
            try {
                List<String> outputLines = bazelCommandRunner.runBazelCommand(bazelWorkspaceRootDirectory, progressMonitor, "info", "execution_root");
                outputLines = BazelCommandRunner.stripInfoLines(outputLines);
                bazelExecRootDirectory = new File(String.join("", outputLines));
            } catch (Exception anyE) {
                throw new IllegalStateException(anyE);
            }
        }
        return bazelExecRootDirectory;
    }

    /**
     * Returns the output base of the current Bazel workspace.
     *
     * @param progressMonitor
     *            can be null
     */
    public File getBazelWorkspaceOutputBase(WorkProgressMonitor progressMonitor) {
        if (bazelOutputBaseDirectory == null) {
            try {
                List<String> outputLines = bazelCommandRunner.runBazelCommand(bazelWorkspaceRootDirectory, progressMonitor, "info", "output_base");
                outputLines = BazelCommandRunner.stripInfoLines(outputLines);
                bazelOutputBaseDirectory = new File(String.join("", outputLines));

            } catch (Exception anyE) {
                throw new IllegalStateException(anyE);
            }
        }
        return bazelOutputBaseDirectory;
    }

    /**
     * Returns the bazel-bin of the current Bazel workspace.
     *
     * @param progressMonitor
     *            can be null
     */
    public File getBazelWorkspaceBin(WorkProgressMonitor progressMonitor) {
        if (bazelBinDirectory == null) {
            try {
                List<String> outputLines = bazelCommandRunner.runBazelCommand(bazelWorkspaceRootDirectory, progressMonitor, "info", "bazel-bin");
                outputLines = BazelCommandRunner.stripInfoLines(outputLines);
                bazelBinDirectory = new File(String.join("", outputLines));
            } catch (Exception anyE) {
                throw new IllegalStateException(anyE);
            }
        }
        return bazelBinDirectory;
    }

    /**
     * Runs the clean command on the workspace.
     */
    public void runBazelClean(WorkProgressMonitor progressMonitor) {
        try {
            bazelCommandRunner.runBazelCommand(bazelWorkspaceRootDirectory, progressMonitor, "clean");
        } catch (IOException | InterruptedException | BazelCommandLineToolConfigurationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the list of targets found in the BUILD files for the given sub-directories. Uses Bazel Query to build the
     * list.
     *
     * @param progressMonitor
     *            can be null
     * @throws BazelCommandLineToolConfigurationException
     */
    public synchronized List<String> listBazelTargetsInBuildFiles(WorkProgressMonitor progressMonitor,
            File... directories) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        StringBuilder builder = new StringBuilder();
        for (File f : directories) {
            builder.append(f.toURI().relativize(bazelWorkspaceRootDirectory.toURI()).getPath()).append("/... ");
        }
        return bazelCommandRunner.runBazelCommand(bazelWorkspaceRootDirectory, progressMonitor, "query", builder.toString());
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
    public synchronized Map<String, AspectPackageInfo> getAspectPackageInfos(String eclipseProjectName,
            Collection<String> targets, WorkProgressMonitor progressMonitor, String caller)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        progressMonitor.subTask("Load Bazel dependency information");
        String logstr = " [prj=" + eclipseProjectName + ", src=" + caller + "]";

        List<String> cacheMissTargets = new ArrayList<>();
        Map<String, AspectPackageInfo> resultMap = new LinkedHashMap<>();
        for (String target : targets) {
            AspectPackageInfo aspectInfo = aspectInfoCache_current.get(target);
            if (aspectInfo != null) {
                LOG.debug("ASPECT CACHE HIT target: {}", target + logstr);
                resultMap.put(target, aspectInfo);
            } else {
                LOG.debug("ASPECT CACHE MISS target: {}", target + logstr);
                cacheMissTargets.add(target);
            }
        }
        progressMonitor.worked(resultMap.size());

        if (cacheMissTargets.size() > 0) {
            List<String> discoveredAspectFilePaths = generateAspectPackageInfoFiles(cacheMissTargets, progressMonitor);
            ImmutableMap<String, AspectPackageInfo> map =
                    AspectPackageInfo.loadAspectFilePaths(discoveredAspectFilePaths);
            resultMap.putAll(map);
            for (String target : map.keySet()) {
                LOG.debug("ASPECT CACHE LOAD target: {}", target + logstr);
                //LOG.debug(map.get(target).toString());
                aspectInfoCache_current.put(target, map.get(target));
                aspectInfoCache_lastgood.put(target, map.get(target));
            }
        }

        if (resultMap.size() < targets.size()) {
            // We have one or more missing targets, it could be because the user introduced a compile error in it and the Aspect wont run.
            // In this case use the last known good result of the Aspect for that target and hope for the best. The lastgood cache is never
            // cleared, so if the Aspect ran correctly at least once since the IDE started it should be here (but possibly out of date depending
            // on what changes were introduced along with the compile error)
            for (String target : targets) {
                if (resultMap.get(target) == null) {
                    AspectPackageInfo aspectInfo = aspectInfoCache_lastgood.get(target);
                    if (aspectInfo != null) {
                        resultMap.put(target, aspectInfo);
                    } else {
                        LOG.debug("ASPECT CACHE FAIL target: {}", target + logstr);
                    }
                }
                progressMonitor.worked(resultMap.size());
            }
        }

        progressMonitor.worked(resultMap.size());

        return resultMap;
    }

    /**
     * Runs the Aspect for the list of passed targets. Returns the list of file paths to the output artifacts created by
     * the Aspects.
     *
     * @throws BazelCommandLineToolConfigurationException
     */
    private synchronized List<String> generateAspectPackageInfoFiles(Collection<String> targets,
            WorkProgressMonitor progressMonitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        List<String> args =
                ImmutableList.<String> builder().add("build").addAll(this.aspectOptions).addAll(targets).build();

        // Strip out the artifact list, keeping the xyz.bzleclipse-build.json files (located in subdirs in the bazel-out path)
        // Line must start with >>> and end with the aspect file suffix
        Function<String, String> filter = t -> t.startsWith(">>>")
                ? (t.endsWith(AspectPackageInfo.ASPECT_FILENAME_SUFFIX) ? t.substring(3) : "") : null;

        List<String> listOfGeneratedFilePaths = this.bazelCommandRunner.runBazelAndGetErrorLines(ConsoleType.WORKSPACE,
            bazelWorkspaceRootDirectory, progressMonitor, args, filter);

        return listOfGeneratedFilePaths;
    }

    /**
     * Clear the entire AspectPackageInfo cache. This flushes the dependency graph for the workspace.
     */
    public synchronized void flushAspectInfoCache() {
        // TODO revisit when to call this
        aspectInfoCache_current.clear();
    }

    /**
     * Clear the AspectPackageInfo cache for the passed targets. This flushes the dependency graph for those targets.
     */
    public synchronized void flushAspectInfoCache(String... targets) {
        // TODO revisit when to call this
        for (String target : targets) {
            aspectInfoCache_current.remove(target);
        }
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
    public synchronized List<BazelMarkerDetails> runBazelBuild(List<String> bazelTargets,
            WorkProgressMonitor progressMonitor, List<String> extraArgs)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        List<String> extraArgsList = ImmutableList.<String> builder().add("build").addAll(this.buildOptions)
                .addAll(extraArgs).add("--").addAll(bazelTargets).build();

        List<String> output = this.bazelCommandRunner.runBazelAndGetErrorLines(bazelWorkspaceRootDirectory, progressMonitor,
            extraArgsList, bazelTargets, new ErrorOutputSelector());
        if (output.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<BazelMarkerDetails> errorDetails = outputParser.getErrorBazelMarkerDetails(output);
            getLogger().debug(getClass(),
                "\n" + String.join("\n", errorDetails.stream().map(d -> d.toString()).collect(Collectors.toList()))
                        + "\n");
            return errorDetails;
        }
    }

    private LoggerFacade getLogger() {
        return LoggerFacade.instance();
    }

    /**
     * Builds and returns a Command instance representing a "bazel run" invocation.
     *
     * @return Command instance
     */
    public Command getBazelRunCommand(List<String> bazelTargets, List<String> extraArgs)
            throws IOException, BazelCommandLineToolConfigurationException {
        List<String> args = ImmutableList.<String> builder().add("run").addAll(this.buildOptions).addAll(extraArgs)
                .add("--").addAll(bazelTargets).build();

        WorkProgressMonitor progressMonitor = null;

        return this.bazelCommandRunner.buildBazelCommand(bazelWorkspaceRootDirectory, progressMonitor, args);
    }

    /**
     * Builds and returns a Command instance representing a "bazel test" invocation.
     *
     * @return Command instance
     */
    public Command getBazelTestCommand(List<String> bazelTargets, List<String> extraArgs)
            throws IOException, BazelCommandLineToolConfigurationException {

        // need to add single method support:
        // --test_filter=com.blah.foo.hello.HelloAgain2Test#testHelloAgain2$

        List<String> args = ImmutableList.<String> builder().add("test").addAll(this.buildOptions)
                .add("--test_output=streamed").add("--test_strategy=exclusive").add("--test_timeout=9999")
                .add("--nocache_test_results").add("--runs_per_test=1").add("--flaky_test_attempts=1").addAll(extraArgs)
                .add("--").addAll(bazelTargets).build();

        WorkProgressMonitor progressMonitor = null;

        return this.bazelCommandRunner.buildBazelCommand(bazelWorkspaceRootDirectory, progressMonitor, args);
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
        if (userSearchString.equals("/") || userSearchString.isEmpty()) {
            return ImmutableList.of("//");
        } else if (userSearchString.contains(":")) {
            // complete targets using `bazel query`
            int idx = userSearchString.indexOf(':');
            final String packageName = userSearchString.substring(0, idx);
            final String targetPrefix = userSearchString.substring(idx + 1);
            List<String> args = ImmutableList.<String> builder().add("query", packageName + ":*").build();
            Function<String, String> selector = line -> {
                int i = line.indexOf(':');
                String s = line.substring(i + 1);
                return !s.isEmpty() && s.startsWith(targetPrefix) ? (packageName + ":" + s) : null;
            };

            List<String> outputLines = this.bazelCommandRunner.runBazelAndGetOuputLines(ConsoleType.NO_CONSOLE,
                bazelWorkspaceRootDirectory, progressMonitor, args, selector);

            ImmutableList.Builder<String> builder = ImmutableList.builder();
            builder.addAll(outputLines);

            if ("all".startsWith(targetPrefix)) {
                builder.add(packageName + ":all");
            }
            if ("*".startsWith(targetPrefix)) {
                builder.add(packageName + ":*");
            }
            return builder.build();
        } else {
            // complete packages
            int lastSlash = userSearchString.lastIndexOf('/');
            final String prefix = lastSlash > 0 ? userSearchString.substring(0, lastSlash + 1) : "";
            final String suffix = lastSlash > 0 ? userSearchString.substring(lastSlash + 1) : userSearchString;
            final String directory = (prefix.isEmpty() || prefix.equals("//")) ? ""
                    : prefix.substring(userSearchString.startsWith("//") ? 2 : 0, prefix.length() - 1);
            File file = directory.isEmpty() ? bazelWorkspaceRootDirectory : new File(bazelWorkspaceRootDirectory, directory);
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            File[] files = file.listFiles((f) -> {
                // Only give directories whose name starts with suffix...
                return f.getName().startsWith(suffix) && f.isDirectory()
                // ...that does not start with '.'...
                        && !f.getName().startsWith(".")
                // ...and is not a Bazel convenience link
                        && (!file.equals(bazelWorkspaceRootDirectory) || !f.getName().startsWith("bazel-"));
            });
            if (files != null) {
                for (File d : files) {
                    builder.add(prefix + d.getName() + "/");
                    if (new File(d, "BUILD").exists()) {
                        builder.add(prefix + d.getName() + ":");
                    }
                }
            }
            if ("...".startsWith(suffix)) {
                builder.add(prefix + "...");
            }
            return builder.build();
        }
    }

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
}
