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

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfoFactory;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelTargetKind;

/**
 * Manages running, collecting, and caching all of the build info aspects for a specific workspace.
 * <p>
 * TODO this belongs in an sdk.aspect package, not buried down in command.internal
 */
public class BazelWorkspaceAspectProcessor {
    private static Logger LOG = LoggerFactory.getLogger(BazelWorkspaceAspectProcessor.class);

    private final BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner;
    private final BazelCommandExecutor bazelCommandExecutor;

    /**
     * These arguments are added to all "bazel build" commands that run for aspect processing. This is public so that
     * your tool can override them as needed.
     */
    public List<String> aspectOptions;

    /**
     * Cache of the Aspect data for each target. key=String target (//a/b/c) value=Set<AspectTargetInfo> data that came
     * from running the aspect.
     *
     * This cache includes wildcard (//a/b/c:*) targets and concrete targets.
     */
    final Map<BazelLabel, Set<AspectTargetInfo>> aspectInfoCache_current = new HashMap<>();

    /**
     * Cache of the Aspect data for each target. key=String target (//a/b/c) value=AspectTargetInfo data that came from
     * running the aspect. This cache is never cleared and is used for cases in which the developer introduces a compile
     * error into the package, such that the Aspect will fail to run.
     */
    final Map<BazelLabel, Set<AspectTargetInfo>> aspectInfoCache_lastgood = new HashMap<>();

    /**
     * Tracks the number of cache hits for getAspectTargetInfos() invocations.
     */
    int numberCacheHits = 0;

    // CTORS

    // if you change or reorder the aspectOptions args below, you will need to update these
    // indices because our test framework looks for args by index
    public static final int ASPECTCMD_EXTERNALREPO_ARGINDEX = 2;
    public static final int ASPECTCMD_TARGETLABEL_ARGINDEX = 11;

    public BazelWorkspaceAspectProcessor(BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner,
            BazelAspectLocation aspectLocation, BazelCommandExecutor bazelCommandExecutor) {
        this.bazelWorkspaceCommandRunner = bazelWorkspaceCommandRunner;
        this.bazelCommandExecutor = bazelCommandExecutor;

        aspectOptions = null;
        if (aspectLocation != null) {
            aspectOptions = new ArrayList<String>();
            aspectOptions.add("--override_repository=bazeljavasdk_aspect=" + aspectLocation.getAspectDirectory());
            aspectOptions.add("--aspects=@bazeljavasdk_aspect" + aspectLocation.getAspectLabel());
            aspectOptions.add("-k");
            aspectOptions.add(
                "--output_groups=intellij-info-generic,intellij-info-java-direct-deps,intellij-resolve-java-direct-deps");
            aspectOptions.add("--nobuild_event_binary_file_path_conversion");
            aspectOptions.add("--noexperimental_run_validations");
            aspectOptions.add("--experimental_show_artifacts");
            aspectOptions.add("--curses=no");
            aspectOptions.add("--progress_in_terminal_title=no");
        }
    }

    /**
     * Override the default arguments that are added to all "bazel build" commands that run for aspect processing. This
     * is an advanced use case.
     */
    public void setAspectOptions(List<String> aspectOptions) {
        this.aspectOptions = aspectOptions;
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
    public synchronized Map<BazelLabel, Set<AspectTargetInfo>> getAspectTargetInfos(Collection<BazelLabel> targets,
            String caller) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        Map<BazelLabel, Set<AspectTargetInfo>> resultMap = new LinkedHashMap<>();
        Collection<BazelLabel> cacheMisses = populateFromCache(targets, resultMap, caller);
        if (!cacheMisses.isEmpty()) {
            loadTargetInfos(cacheMisses, resultMap, caller);
        }
        return resultMap;
    }

    /**
     * Clear the entire AspectTargetInfo cache. This flushes the dependency graph for the workspace.
     */
    public synchronized void flushAspectInfoCache() {
        aspectInfoCache_current.clear();
    }

    /**
     * Clear the AspectTargetInfo cache for the passed target. This flushes the dependency graph for those target.
     */
    public synchronized void flushAspectInfoCache(BazelLabel target) {
        // the target may not even be in cache, that is ok, just try to remove it from both current and wildcard caches
        // if the target exists in either it will get flushed
        aspectInfoCache_current.remove(target);
    }

    /**
     * Clear the AspectTargetInfo cache for the passed targets. This flushes the dependency graph for those targets.
     */
    public synchronized void flushAspectInfoCache(Set<BazelLabel> targets) {
        for (BazelLabel target : targets) {
            // the target may not even be in cache, that is ok, just try to remove it from both current and wildcard caches
            // if the target exists in either it will get flushed
            aspectInfoCache_current.remove(target);
        }
    }

    /**
     * Clear the AspectTargetInfo cache for the passed package. This flushes the dependency graph for any target that
     * contains the package name.
     */
    public synchronized Set<BazelLabel> flushAspectInfoCacheForPackage(BazelLabel bazelPackage) {
        Set<BazelLabel> flushedTargets = new LinkedHashSet<>();

        // the target may not even be in cache, that is ok, just try to remove it from both current and wildcard caches
        // if the target exists in either it will get flushed
        Iterator<BazelLabel> iter = aspectInfoCache_current.keySet().iterator();
        while (iter.hasNext()) {
            BazelLabel key = iter.next();
            if (key.getPackagePath().equals(bazelPackage.getPackagePath())) {
                flushedTargets.add(key);
                iter.remove();
            }
        }
        return flushedTargets;
    }

    // INTERNALS

    /**
     * Populates the specified resultMap from cache. Returns the cache misses.
     */
    private synchronized Collection<BazelLabel> populateFromCache(Collection<BazelLabel> labels,
            Map<BazelLabel, Set<AspectTargetInfo>> resultMap, String caller) {
        List<BazelLabel> cacheMisses = new ArrayList<>();
        for (BazelLabel target : labels) {
            String logstr = getLogStr(target, caller);
            Set<AspectTargetInfo> aspectInfos = aspectInfoCache_current.get(target);
            if (aspectInfos == null) {
                LOG.info("Aspect data not found in cache for: " + target + logstr);
                cacheMisses.add(target);
            } else {
                LOG.info("Aspect data found in cache for: " + target + logstr);
                resultMap.put(target, aspectInfos);
                numberCacheHits++;
            }
        }
        return cacheMisses;
    }

    private synchronized void loadTargetInfos(Collection<BazelLabel> cacheMisses,
            Map<BazelLabel, Set<AspectTargetInfo>> resultMap, String caller)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        LOG.debug("Starting generation of Aspect files for {} packages.",  cacheMisses.size());
        List<String> discoveredAspectFilePaths = generateAspectTargetInfoFiles(cacheMisses);
        LOG.debug("Finished generation of Aspect files for {} packages.",  cacheMisses.size());

        if(discoveredAspectFilePaths.isEmpty()) {
            // this cannot be right
            throw new IOException(format("No Aspect files generated for: %s", cacheMisses.stream().map(BazelLabel::toString).collect(joining(", "))));
        }

        Map<BazelLabel, AspectTargetInfo> aspectInfos = loadAspectFilePaths(discoveredAspectFilePaths);

        if (aspectInfos.isEmpty()) {
            // We were not able to load any aspects, this generally indicates some sort of error condition;
            // it could be because the user introduced a compile error in it and the Aspect wont run.
            // In this case use the last known good result of the Aspect for that target and hope for the best. The lastgood cache is never
            // cleared, so if the Aspect ran correctly at least once since the IDE started it should be here (but possibly out of date depending
            // on what changes were introduced along with the compile error)
            for (BazelLabel label : cacheMisses) {
                Set<AspectTargetInfo> lastgood = aspectInfoCache_lastgood.get(label);
                if (lastgood == null) {
                    LOG.debug("Aspect execution failed (all) for target: {}", getLogStr(label, caller));
                } else {
                    resultMap.put(label, lastgood);
                }
            }
        } else {
            Map<BazelLabel, Set<AspectTargetInfo>> owningLabelToAspectInfos = new HashMap<>();
            for (BazelLabel label : cacheMisses) {
                Map<BazelLabel, Set<AspectTargetInfo>> m = assignAspectsToOwningLabel(label, aspectInfos);
                owningLabelToAspectInfos.putAll(m);
            }
            for (BazelLabel label : owningLabelToAspectInfos.keySet()) {
                Set<AspectTargetInfo> infos = owningLabelToAspectInfos.get(label);
                aspectInfoCache_current.put(label, infos);
                aspectInfoCache_lastgood.put(label, infos);
                LOG.debug("Aspect data loaded for target: " + label + getLogStr(label, caller));
            }
            for (BazelLabel label : cacheMisses) {
                // since we just populated the caches above, we should now find results
                // this could be done in the loop above, but this is good sanity
                Set<AspectTargetInfo> atis = aspectInfoCache_current.get(label);
                if (atis == null) {
                    LOG.error("Aspect execution failed (single) for target: " + label + getLogStr(label, caller));
                    atis = Collections.emptySet();
                }
                resultMap.put(label, atis);
            }
        }
    }

    /**
     * This method creates and returns a mapping of a Label to the AspectTargetInfo (ATI) instances belonging to that
     * Label. These ATI instances are the transitive closure of ATIs referenced by the mapped Label.
     *
     * This method behaves differently based on whatever the specified requestingLabel is a wildcard label or a concrete
     * Label:
     *
     * If the specified requestedLabel is concrete (//a/b/c), this method returns a single mapping: BazelLabel(//a/b/c)
     * -> Set of all ATI instances (transitive closure) owned by that label.
     *
     * If the specified requestingLabel is a wildcard label (//a/b/c:*), this method looks at all ATIs passed into this
     * method and returns a mapping for each that has the same Bazel Package as the specified owningLabel, in additional
     * to the mapping for the wildcard target.
     *
     * For example, with ATIs for these targets: //a/b/c:t1 and //a/b/c:t2 and a requestingLabel of //a/b/c:*, this
     * method returns:
     *
     * //a/b/c:* -> all specified ATIs //a/b/c:t1 -> transitive closure of ATIs for t1 //a/b/c:t2 -> transitive closure
     * of ATIs for t2
     */
    private static Map<BazelLabel, Set<AspectTargetInfo>> assignAspectsToOwningLabel(BazelLabel requestingLabel,
            Map<BazelLabel, AspectTargetInfo> depNameToTargetInfo) {
        Map<BazelLabel, Set<AspectTargetInfo>> transitivesClosures = new HashMap<>();

        // find starting point, based on target - this is trivial, but we also support wildcard
        // targets (so that we can run a single bazal build cmd and get all aspects)
        for (AspectTargetInfo ati : depNameToTargetInfo.values()) {
            BazelLabel currentLabel = new BazelLabel(ati.getLabelPath());
            if (requestingLabel.isConcrete()) {
                if (requestingLabel.equals(currentLabel)) {
                    Set<AspectTargetInfo> allDeps = getTransitiveClosure(ati, depNameToTargetInfo);
                    transitivesClosures.put(new BazelLabel(ati.getLabelPath()), allDeps);
                }
            } else {
                // all targets in the requested package qualify
                if (currentLabel.getPackagePath().equals(requestingLabel.getPackagePath())) {
                    Set<AspectTargetInfo> allDeps = getTransitiveClosure(ati, depNameToTargetInfo);
                    transitivesClosures.put(new BazelLabel(ati.getLabelPath()), allDeps);
                }
            }
        }

        if (!requestingLabel.isConcrete()) {
            // also return a mapping of wildcard target -> all AspectTargetInfo instances
            transitivesClosures.put(requestingLabel, new HashSet<>(depNameToTargetInfo.values()));
        }

        return transitivesClosures;

    }

    private static Set<AspectTargetInfo> getTransitiveClosure(AspectTargetInfo aspectTargetInfo,
            Map<BazelLabel, AspectTargetInfo> depNameToTargetInfo) {
        Set<AspectTargetInfo> allDeps = new HashSet<>();
        List<AspectTargetInfo> queue = new ArrayList<>();
        Set<String> skippedLabels = new HashSet<>();
        Set<String> visitedLabels = new HashSet<>();
        queue.add(aspectTargetInfo);
        while (!queue.isEmpty()) {
            AspectTargetInfo ati = queue.remove(0);

            String thisLabel = ati.getLabelPath();
            if (visitedLabels.contains(thisLabel)) {
                // we have already visited this dependency (through another path) just skip it
                continue;
            }
            visitedLabels.add(thisLabel);

            if (ati != aspectTargetInfo) {
                allDeps.add(ati);
            }
            List<String> depLabels = ati.getDeps();
            for (String label : depLabels) {
                if (skippedLabels.contains(label)) {
                    // this is a dep that is not something we work with, so we ignore it
                    continue;
                }
                if (visitedLabels.contains(label)) {
                    // we have already visited this dependency (through another path) just skip it
                    continue;
                }

                BazelLabel depLabel = new BazelLabel(label);
                AspectTargetInfo dep = depNameToTargetInfo.get(depLabel);
                if (dep == null) {
                    LOG.debug("No AspectTargetInfo exists for {}; it and its descendents are excluded from analysis.", label);
                    skippedLabels.add(label);
                } else {
                    queue.add(dep);
                }
            }
        }

        // now add this aspect to the transitive closure if test or import (TODO why?)
        BazelTargetKind kind = aspectTargetInfo.getKind();
        if (kind != null) {
            if (kind.isKind("java_test")) {
                allDeps.add(aspectTargetInfo);
            } else if (kind.isKind("java_import")) {
                allDeps.add(aspectTargetInfo);
            }
        } else {
            kind = aspectTargetInfo.getKind();
            LOG.debug("AspectInfo {} does not have an associated target kind.", aspectTargetInfo.getLabel().getLabelPath());
        }

        return Collections.unmodifiableSet(allDeps);
    }

    /**
     * Runs the Aspect for the list of passed targets. Returns the list of file paths to the output artifacts created by
     * the Aspects.
     *
     * @throws BazelCommandLineToolConfigurationException
     */
    private synchronized List<String> generateAspectTargetInfoFiles(Collection<BazelLabel> targets)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Parameter [targets] cannot be empty.");
        }
        File bazelWorkspaceRootDirectory = bazelWorkspaceCommandRunner.getBazelWorkspaceRootDirectory();

        BazelLabel[] targetsArray = targets.toArray(new BazelLabel[] {});
        int lastValidTargetIndex = targets.size() - 1;
        int currentTargetIndex = 0;
        List<String> listOfGeneratedFilePaths = new ArrayList<>();

        // run the aspect generation for the target labels, we want to minimize the number of bazel invocations
        // because there is a few seconds of overhead for each invocation, but we only do 25 at a time so we get
        // progress log messages along the way
        while (currentTargetIndex <= lastValidTargetIndex) {
            int startTargetIndex = currentTargetIndex;
            List<String> args = new ArrayList<>();
            args.add("build");
            args.addAll(aspectOptions);
            for (int i = 0; i < 25; i++) {
                if (currentTargetIndex > lastValidTargetIndex) {
                    break;
                }
                String label = targetsArray[currentTargetIndex].toString();
                currentTargetIndex++;
                args.add(label);
            }

            // Strip out the artifact list, keeping the xyz.bzljavasdk-data.json files (located in subdirs in the bazel-out path)
            // Line must start with >>> and end with the aspect file suffix
            LOG.info("Running command to generate aspect file for labels indexed [" + startTargetIndex + "] through ["
                    + (startTargetIndex + 25) + "] out of the total [" + (lastValidTargetIndex + 1) + "]");
            Function<String, String> filter = (t) -> {
                LOG.info("Aspect output line: " + t);
                String r = null;
                if (t.startsWith(">>>")) {
                    if (t.endsWith(AspectTargetInfoFactory.ASPECT_FILENAME_SUFFIX)) {
                        LOG.info("  Aspect output (json file): {}", t);
                        r = t.substring(3);
                    } else {
                        LOG.info("  Aspect output (ignored): {}", t);
                        r = null;
                    }
                } else {
                    LOG.info("  Aspect output (ignored): {}", t);
                    r = null;
                }
                return r;
            };

            List<String> partialListOfGeneratedFilePaths =
                    bazelCommandExecutor.runBazelAndGetErrorLinesIgnoringExitCode(ConsoleType.WORKSPACE, bazelWorkspaceRootDirectory,
                        null, args, filter, BazelCommandExecutor.TIMEOUT_INFINITE);
            listOfGeneratedFilePaths.addAll(partialListOfGeneratedFilePaths);
        }
        return listOfGeneratedFilePaths;
    }

    private static String getLogStr(BazelLabel target, String caller) {
        return " [target=" + target + ", src=" + caller + "]";
    }

    public static Map<BazelLabel, AspectTargetInfo> loadAspectFilePaths(List<String> aspectFilePaths)
            throws IOException, InterruptedException {
        Map<BazelLabel, AspectTargetInfo> bzToAtis = new HashMap<>();

        if (aspectFilePaths.size() == 0) {
            LOG.error(
                "No results returned from running aspects. This normally means there is a build error in the BUILD file. "
                        + "Please run 'bazel build //...' to verify that the workspace is valid. ");
            return bzToAtis;
        }

        Map<String, AspectTargetInfo> lToAtis = AspectTargetInfoFactory.loadAspectFilePaths(aspectFilePaths);
        if (lToAtis.isEmpty()) {
            LOG.error("No aspect files were parsed successfully. Aspect file list size: " + aspectFilePaths.size());
            return bzToAtis;
        }
        for (Map.Entry<String, AspectTargetInfo> e : lToAtis.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                // bug
                LOG.error("Null key returned from AspectTargetInfoFactory.loadAspectFilePaths");
                continue;
            }
            AspectTargetInfo value = e.getValue();
            bzToAtis.put(new BazelLabel(key), value);
            LOG.info("Aspect for {} loaded successfully.", key);
        }
        return bzToAtis;
    }
}
