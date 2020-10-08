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
package com.salesforce.bazel.sdk.command.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Manages running, collecting, and caching all of the build info aspects for a specific workspace.
 */
public class BazelWorkspaceAspectHelper {
    static final LogHelper LOG = LogHelper.log(BazelWorkspaceAspectHelper.class);;

    private final BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner;
    private final BazelCommandExecutor bazelCommandExecutor;

    /**
     * These arguments are added to all "bazel build" commands that run for aspect processing
     */
    private List<String> aspectOptions;

    /**
     * Cache of the Aspect data for each target. key=String target (//a/b/c) value=Set<AspectTargetInfo> data that came
     * from running the aspect.
     */
    @VisibleForTesting
    final Map<String, Set<AspectTargetInfo>> aspectInfoCache_current = new HashMap<>();

    /**
     * For wildcard targets //a/b/c:* we need to capture the resulting aspects that come from evaluation so that the
     * underlying list of aspects can be rebuilt from cache
     */
    @VisibleForTesting
    final Map<String, Set<String>> aspectInfoCache_wildcards = new HashMap<>();

    /**
     * Cache of the Aspect data for each target. key=String target (//a/b/c) value=AspectTargetInfo data that came from
     * running the aspect. This cache is never cleared and is used for cases in which the developer introduces a compile
     * error into the package, such that the Aspect will fail to run.
     */
    @VisibleForTesting
    final Map<String, Set<AspectTargetInfo>> aspectInfoCache_lastgood = new HashMap<>();

    /**
     * Tracks the number of cache hits for getAspectTargetInfos() invocations.
     */
    @VisibleForTesting
    int numberCacheHits = 0;

    // CTORS

    public BazelWorkspaceAspectHelper(BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner,
            BazelAspectLocation aspectLocation, BazelCommandExecutor bazelCommandExecutor) {
        this.bazelWorkspaceCommandRunner = bazelWorkspaceCommandRunner;
        this.bazelCommandExecutor = bazelCommandExecutor;

        this.aspectOptions = null;
        if (aspectLocation != null) {
            this.aspectOptions = ImmutableList.<String> builder()
                    .add("--override_repository=local_eclipse_aspect=" + aspectLocation.getAspectDirectory(),
                        "--aspects=@local_eclipse_aspect" + aspectLocation.getAspectLabel(), "-k",
                        "--output_groups=json-files,classpath-jars,-_,-defaults", "--experimental_show_artifacts")
                    .build();
        }
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
     * @throws BazelCommandLineToolConfigurationException
     */
    public synchronized Map<String, Set<AspectTargetInfo>> getAspectTargetInfos(Collection<String> targets,
            WorkProgressMonitor progressMonitor, String caller)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        if (progressMonitor != null) {
            progressMonitor.subTask("Load Bazel dependency information");
        }
        Map<String, Set<AspectTargetInfo>> resultMap = new LinkedHashMap<>();

        for (String target : targets) {
            // is this a wildcard target? we have to handle that differently
            // TODO we are no longer using this now that we query each target directly
            if (target.endsWith("*")) {
                Set<String> wildcardTargets = aspectInfoCache_wildcards.get(target);
                if (wildcardTargets != null) {
                    // we know what sub-targets resolve from the wildcard target, so add each sub-target aspect
                    for (String wildcardTarget : wildcardTargets) {
                        getAspectTargetInfoForTarget(wildcardTarget, progressMonitor, caller, resultMap);
                    }
                } else {
                    // we haven't seen this wildcard before, we need to ask bazel what sub-targets it maps to
                    Map<String, Set<AspectTargetInfo>> wildcardResultMap = new LinkedHashMap<>();
                    getAspectTargetInfoForTarget(target, progressMonitor, caller, wildcardResultMap);
                    resultMap.putAll(wildcardResultMap);
                    aspectInfoCache_wildcards.put(target, wildcardResultMap.keySet());
                }
            } else {
                getAspectTargetInfoForTarget(target, progressMonitor, caller, resultMap);
            }
        }

        if (progressMonitor != null) {
            progressMonitor.worked(resultMap.size());
        }

        return resultMap;
    }

    /**
     * Clear the entire AspectTargetInfo cache. This flushes the dependency graph for the workspace.
     */
    public synchronized void flushAspectInfoCache() {
        this.aspectInfoCache_current.clear();
        this.aspectInfoCache_wildcards.clear();
    }

    /**
     * Clear the AspectTargetInfo cache for the passed target. This flushes the dependency graph for those target.
     */
    public synchronized void flushAspectInfoCache(String target) {
        // the target may not even be in cache, that is ok, just try to remove it from both current and wildcard caches
        // if the target exists in either it will get flushed
        this.aspectInfoCache_current.remove(target);
        this.aspectInfoCache_wildcards.remove(target);
    }

    /**
     * Clear the AspectTargetInfo cache for the passed targets. This flushes the dependency graph for those targets.
     */
    public synchronized void flushAspectInfoCache(Set<String> targets) {
        for (String target : targets) {
            // the target may not even be in cache, that is ok, just try to remove it from both current and wildcard caches
            // if the target exists in either it will get flushed
            this.aspectInfoCache_current.remove(target);
            this.aspectInfoCache_wildcards.remove(target);
        }
    }

    /**
     * Clear the AspectTargetInfo cache for the passed package. This flushes the dependency graph for any target that
     * contains the package name.
     */
    public synchronized Set<String> flushAspectInfoCacheForPackage(String packageName) {
        Set<String> flushedTargets = new LinkedHashSet<>();

        // the target may not even be in cache, that is ok, just try to remove it from both current and wildcard caches
        // if the target exists in either it will get flushed
        Iterator<String> iter = this.aspectInfoCache_current.keySet().iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            if (key.contains(packageName)) {
                flushedTargets.add(key);
                iter.remove();
            }
        }
        iter = this.aspectInfoCache_wildcards.keySet().iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            if (key.contains(packageName)) {
                flushedTargets.add(key);
                iter.remove();
            }
        }

        return flushedTargets;
    }

    // INTERNALS

    private void getAspectTargetInfoForTarget(String target, WorkProgressMonitor progressMonitor, String caller,
            Map<String, Set<AspectTargetInfo>> resultMap)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        String logstr = " [target=" + target + ", src=" + caller + "]";

        Set<AspectTargetInfo> aspectInfos = aspectInfoCache_current.get(target);
        if (aspectInfos != null) {
            LOG.info("ASPECT CACHE HIT target: " + target + logstr);
            resultMap.put(target, aspectInfos);
            this.numberCacheHits++;
        } else {
            LOG.info("ASPECT CACHE MISS target: " + target + logstr);
            List<String> lookupTargets = new ArrayList<>();
            lookupTargets.add(target);
            List<String> discoveredAspectFilePaths = generateAspectTargetInfoFiles(lookupTargets, progressMonitor);
            ImmutableMap<String, AspectTargetInfo> map =
                    AspectTargetInfo.loadAspectFilePaths(discoveredAspectFilePaths);

            if (map.size() == 0) {
                // still don't have the aspect for the target, use the last known one that computed
                // it could be because the user introduced a compile error in it and the Aspect wont run.
                // In this case use the last known good result of the Aspect for that target and hope for the best. The lastgood cache is never
                // cleared, so if the Aspect ran correctly at least once since the IDE started it should be here (but possibly out of date depending
                // on what changes were introduced along with the compile error)
                Set<AspectTargetInfo> lastgood = aspectInfoCache_lastgood.get(target);
                if (lastgood != null) {
                    resultMap.put(target, lastgood);
                } else {
                    LOG.info("ASPECT CACHE FAIL target: " + target + logstr);
                }
            } else {
                Set<AspectTargetInfo> values = new HashSet<>();
                for (AspectTargetInfo info : map.values()) {
                    values.add(info); // TODO this is super dumb, immutablemap causes type issues, fix it
                }
                resultMap.put(target, values);
                LOG.info("ASPECT CACHE LOAD target: " + target + logstr);
                aspectInfoCache_current.put(target, values);
                aspectInfoCache_lastgood.put(target, values);
            }
        }

        if (progressMonitor != null) {
            progressMonitor.worked(resultMap.size());
        }
    }

    /**
     * Runs the Aspect for the list of passed targets. Returns the list of file paths to the output artifacts created by
     * the Aspects.
     *
     * @throws BazelCommandLineToolConfigurationException
     */
    private synchronized List<String> generateAspectTargetInfoFiles(Collection<String> targets,
            WorkProgressMonitor progressMonitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        List<String> args =
                ImmutableList.<String> builder().add("build").addAll(this.aspectOptions).addAll(targets).build();

        // Strip out the artifact list, keeping the xyz.bzleclipse-build.json files (located in subdirs in the bazel-out path)
        // Line must start with >>> and end with the aspect file suffix
        Function<String, String> filter = t -> t.startsWith(">>>")
                ? (t.endsWith(AspectTargetInfo.ASPECT_FILENAME_SUFFIX) ? t.substring(3) : "") : null;

        List<String> listOfGeneratedFilePaths = this.bazelCommandExecutor.runBazelAndGetErrorLines(
            ConsoleType.WORKSPACE, this.bazelWorkspaceCommandRunner.getBazelWorkspaceRootDirectory(), progressMonitor,
            args, filter, BazelCommandExecutor.TIMEOUT_INFINITE);

        return listOfGeneratedFilePaths;
    }

}
