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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelBuildFile;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelLabelUtil;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Helper that knows how to run bazel query commands.
 */
public class BazelQueryHelper {

    private static final LogHelper LOG = LogHelper.log(BazelQueryHelper.class);

    /**
     * Underlying command invoker which takes built Command objects and executes them.
     */
    private final BazelCommandExecutor bazelCommandExecutor;

    private final Map<BazelLabel, BazelBuildFile> buildFileCache = new HashMap<>();

    public BazelQueryHelper(BazelCommandExecutor bazelCommandExecutor) {
        this.bazelCommandExecutor = bazelCommandExecutor;
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
    public synchronized List<String> listBazelTargetsInBuildFiles(File bazelWorkspaceRootDirectory,
            WorkProgressMonitor progressMonitor, File... directories)
                    throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        List<String> argBuilder = new ArrayList<>();
        argBuilder.add("query");
        for (File f : directories) {
            String directoryPath = f.toURI().relativize(bazelWorkspaceRootDirectory.toURI()).getPath();
            argBuilder.add(directoryPath + "/...");
        }
        return bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory, progressMonitor,
            argBuilder, t -> t, BazelCommandExecutor.TIMEOUT_INFINITE);
    }

    /**
     * Returns the list of targets, with type data, found in a BUILD files for the given package. Uses Bazel Query to
     * build the list.
     *
     * @param bazelPackageName
     *            the label path that identifies the package where the BUILD file lives (//projects/libs/foo)
     */
    public synchronized Collection<BazelBuildFile> queryBazelTargetsInBuildFile(File bazelWorkspaceRootDirectory, Collection<BazelLabel> bazelLabels)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {

        if (bazelLabels.isEmpty()) {
            return Collections.singletonList(new BazelBuildFile("//..."));
        }

        Collection<BazelLabel> cacheMisses = new HashSet<>();
        Collection<BazelBuildFile> buildFiles = new HashSet<>();
        Map<BazelLabel, Collection<BazelLabel>> packageToLabels = BazelLabelUtil.groupByPackage(bazelLabels);

        for (BazelLabel pack : packageToLabels.keySet()) {
            BazelBuildFile buildFile = buildFileCache.get(pack);
            if (buildFile == null) {
                cacheMisses.addAll(packageToLabels.get(pack));
                LOG.info("QUERY CACHE MISS package: " + pack);
            } else {
                buildFiles.add(buildFile);
                LOG.info("QUERY CACHE GET package: " + pack);
            }
        }

        if (!cacheMisses.isEmpty()) {
            Collection<BazelBuildFile> loadedBuildFiles = runQuery(cacheMisses, bazelWorkspaceRootDirectory);
            buildFiles.addAll(loadedBuildFiles);
        }
        return buildFiles;
    }

    // runs query and populates cache, returns loaded BazelBuildFile instances
    private Collection<BazelBuildFile> runQuery(Collection<BazelLabel> bazelLabels, File bazelWorkspaceRootDirectory) throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        String labels = bazelLabels.stream().map(BazelLabel::getLabel).collect(Collectors.joining(" "));

        // bazel query 'kind(rule, [label]:*)' --output label_kind

        List<String> argBuilder = new ArrayList<>();
        argBuilder.add("query");
        argBuilder.add("kind(rule, set(" + labels + "))");
        argBuilder.add("--output");
        argBuilder.add("label_kind");
        List<String> resultLines = bazelCommandExecutor.runBazelAndGetOutputLines(bazelWorkspaceRootDirectory,
            null, argBuilder, t -> t, BazelCommandExecutor.TIMEOUT_INFINITE);

        // Sample Output:  (format: rule_type 'rule' label)
        // java_binary rule //projects/libs/apple/apple-api:apple-main
        // java_test rule //projects/libs/apple/apple-api:apple-api-test2
        // java_library rule //projects/libs/apple/apple-api:apple-api

        Map<BazelLabel, String> labelToRuleType = new HashMap<>();
        for (String resultLine : resultLines) {
            String[] tokens = resultLine.split(" ");
            if (tokens.length != 3) {
                continue;
            }
            String ruleType = tokens[0];
            String targetLabel = tokens[2];
            labelToRuleType.put(new BazelLabel(targetLabel), ruleType);
        }

        Set<BazelLabel> unprocessed = new HashSet<>(BazelLabelUtil.groupByPackage(bazelLabels).keySet());

        Map<BazelLabel, Collection<BazelLabel>> packageToLabel = BazelLabelUtil.groupByPackage(labelToRuleType.keySet());

        Collection<BazelBuildFile> buildFiles = new HashSet<>();
        for (BazelLabel pack : packageToLabel.keySet()) {
            BazelBuildFile buildFile = new BazelBuildFile(pack.getLabel());
            buildFileCache.put(pack, buildFile);
            LOG.info("QUERY CACHE PUT package: " + pack);
            buildFiles.add(buildFile);
            unprocessed.remove(pack);
            for (BazelLabel target : packageToLabel.get(pack)) {
                String ruleType = Objects.requireNonNull(labelToRuleType.get(target));
                buildFile.addTarget(ruleType, target.getLabel());
            }
        }

        // some packages may not have any targets - they need to be accounted for
        for (BazelLabel pack : unprocessed) {
            BazelBuildFile buildFile = new BazelBuildFile(pack.getLabel());
            buildFileCache.put(pack, buildFile);
            LOG.info("QUERY CACHE PUT (no targets) package: " + pack);
        }

        return buildFiles;
    }

    public void flushCache(BazelLabel bazelPackageName) {
        BazelLabel pack = bazelPackageName.toDefaultPackageLabel();
        BazelBuildFile previousValue = buildFileCache.remove(pack);
        if (previousValue != null) {
            LOG.info("QUERY CACHE FLUSH package " + pack);
        }
    }
}
