/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.google.idea.blaze.base.command.buildresult;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import java.io.InputStream;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Queues;
import com.google.common.collect.SetMultimap;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.NamedSetOfFiles;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.OutputGroup;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.protobuf.Timestamp;

/** A data class representing blaze's build event protocol (BEP) output for a build. */
public final class ParsedBepOutput {

    private static class FileSet {
        private static class Builder {
            NamedSetOfFiles namedSet;
            String configId;
            final Set<String> outputGroups = new HashSet<>();
            final Set<String> targets = new HashSet<>();

            Builder addOutputGroups(Set<String> outputGroups) {
                this.outputGroups.addAll(outputGroups);
                return this;
            }

            Builder addTargets(Set<String> targets) {
                this.targets.addAll(targets);
                return this;
            }

            FileSet build(Map<String, String> configIdToMnemonic, Timestamp startTime) {
                return new FileSet(namedSet, configIdToMnemonic.get(configId), startTime, outputGroups, targets);
            }

            boolean isValid(Map<String, String> configIdToMnemonic) {
                return (namedSet != null) && (configId != null) && (configIdToMnemonic.get(configId) != null);
            }

            Builder setConfigId(String configId) {
                this.configId = configId;
                return this;
            }

            Builder setNamedSet(NamedSetOfFiles namedSet) {
                this.namedSet = namedSet;
                return this;
            }

            Builder updateFromParent(Builder parent) {
                configId = parent.configId;
                outputGroups.addAll(parent.outputGroups);
                targets.addAll(parent.targets);
                return this;
            }
        }

        static Builder builder() {
            return new Builder();
        }

        private final ImmutableList<OutputArtifact> parsedOutputs;

        private final ImmutableSet<String> outputGroups;

        private final ImmutableSet<String> targets;

        FileSet(NamedSetOfFiles namedSet, String configuration, Timestamp startTime, Set<String> outputGroups,
                Set<String> targets) {
            parsedOutputs = parseFiles(namedSet, configuration, startTime);
            this.outputGroups = ImmutableSet.copyOf(outputGroups);
            this.targets = ImmutableSet.copyOf(targets);
        }

        private Stream<BepArtifactData> toPerArtifactData() {
            return parsedOutputs.stream().map(a -> new BepArtifactData(a, outputGroups, targets));
        }
    }

    /**
     * Only top-level targets have configuration mnemonic, producing target, and output group data explicitly provided
     * in BEP. This method fills in that data for the transitive closure.
     */
    private static ImmutableMap<String, FileSet> fillInTransitiveFileSetData(Map<String, FileSet.Builder> fileSets,
            Set<String> topLevelFileSets, Map<String, String> configIdToMnemonic, Timestamp startTime) {
        Deque<String> toVisit = Queues.newArrayDeque(topLevelFileSets);
        Set<String> visited = new HashSet<>(topLevelFileSets);
        while (!toVisit.isEmpty()) {
            var setId = toVisit.remove();
            var fileSet = fileSets.get(setId);
            if (fileSet.namedSet == null) {
                continue;
            }
            fileSet.namedSet.getFileSetsList().stream().map(NamedSetOfFilesId::getId).filter(s -> !visited.contains(s))
                    .forEach(child -> {
                        fileSets.get(child).updateFromParent(fileSet);
                        toVisit.add(child);
                        visited.add(child);
                    });
        }
        return fileSets.entrySet().stream().filter(e -> e.getValue().isValid(configIdToMnemonic))
                .collect(toImmutableMap(Entry::getKey, e -> e.getValue().build(configIdToMnemonic, startTime)));
    }

    private static List<String> getFileSets(OutputGroup group) {
        return group.getFileSetsList().stream().map(NamedSetOfFilesId::getId).collect(Collectors.toList());
    }

    /** Returns a copy of a {@link NamedSetOfFiles} with interned string references. */
    private static NamedSetOfFiles internNamedSet(NamedSetOfFiles namedSet, Interner<String> interner) {
        return namedSet.toBuilder().clearFiles().addAllFiles(namedSet
                .getFilesList().stream().map(file -> file.toBuilder()
                        // The digest is not used when parsing output artifacts
                        .setDigest("").setUri(interner.intern(file.getUri())).setName(interner.intern(file.getName()))
                        .clearPathPrefix()
                        .addAllPathPrefix(file.getPathPrefixList().stream().map(interner::intern)
                                .collect(Collectors.toUnmodifiableList()))
                        .build())
                .collect(Collectors.toUnmodifiableList())).build();
    }

    /** Parses BEP events into {@link ParsedBepOutput} */
    public static ParsedBepOutput parseBepArtifacts(BuildEventStreamProvider stream) throws BuildEventStreamException {
        return parseBepArtifacts(stream, null);
    }

    /**
     * Parses BEP events into {@link ParsedBepOutput}. String references in {@link NamedSetOfFiles} are interned to
     * conserve memory.
     *
     * <p>
     * BEP protos often contain many duplicate strings both within a single stream and across shards running in
     * parallel, so a {@link Interner} is used to share references.
     */
    public static ParsedBepOutput parseBepArtifacts(BuildEventStreamProvider stream, Interner<String> interner)
            throws BuildEventStreamException {

        if (interner == null) {
            interner = Interners.newStrongInterner();
        }

        BuildEvent event;
        Map<String, String> configIdToMnemonic = new HashMap<>();
        Set<String> topLevelFileSets = new HashSet<>();
        Map<String, FileSet.Builder> fileSets = new LinkedHashMap<>();
        ImmutableSetMultimap.Builder<String, String> targetToFileSets = ImmutableSetMultimap.builder();
        String localExecRoot = null;
        String buildId = null;
        Timestamp startTime = null;
        var buildResult = BuildResult.SUCCESS;
        var emptyBuildEventStream = true;

        while ((event = stream.getNext()) != null) {
            emptyBuildEventStream = false;
            switch (event.getId().getIdCase()) {
                case WORKSPACE:
                    localExecRoot = event.getWorkspaceInfo().getLocalExecRoot();
                    continue;
                case CONFIGURATION:
                    configIdToMnemonic.put(event.getId().getConfiguration().getId(),
                        event.getConfiguration().getMnemonic());
                    continue;
                case NAMED_SET:
                    var namedSet = internNamedSet(event.getNamedSetOfFiles(), interner);
                    fileSets.compute(event.getId().getNamedSet().getId(),
                        (k, v) -> v != null ? v.setNamedSet(namedSet) : FileSet.builder().setNamedSet(namedSet));
                    continue;
                case TARGET_COMPLETED:
                    var label = event.getId().getTargetCompleted().getLabel();
                    var configId = event.getId().getTargetCompleted().getConfiguration().getId();

                    event.getCompleted().getOutputGroupList().forEach(o -> {
                        var sets = getFileSets(o);
                        targetToFileSets.putAll(label, sets);
                        topLevelFileSets.addAll(sets);
                        for (String id : sets) {
                            fileSets.compute(id, (k, v) -> {
                                var builder = (v != null) ? v : FileSet.builder();
                                return builder.setConfigId(configId).addOutputGroups(ImmutableSet.of(o.getName()))
                                        .addTargets(ImmutableSet.of(label));
                            });
                        }
                    });
                    continue;
                case STARTED:
                    buildId = Strings.emptyToNull(event.getStarted().getUuid());
                    startTime = event.getStarted().getStartTime();
                    continue;
                case BUILD_FINISHED:
                    buildResult = BuildResult.fromExitCode(event.getFinished().getExitCode().getCode());
                    continue;
                default: // continue
            }
        }
        // If stream is empty, it means that service failed to retrieve any blaze build event from build
        // event stream. This should not happen if a build start correctly.
        if (emptyBuildEventStream) {
            throw new BuildEventStreamException("No build events found");
        }
        var filesMap = fillInTransitiveFileSetData(fileSets, topLevelFileSets, configIdToMnemonic, startTime);
        return new ParsedBepOutput(buildId, localExecRoot, filesMap, targetToFileSets.build(), startTime, buildResult,
                stream.getBytesConsumed());
    }

    /** Parses BEP events into {@link ParsedBepOutput} */
    public static ParsedBepOutput parseBepArtifacts(InputStream bepStream) throws BuildEventStreamException {
        return parseBepArtifacts(BuildEventStreamProvider.fromInputStream(bepStream));
    }

    private static ImmutableList<OutputArtifact> parseFiles(NamedSetOfFiles namedSet, String config,
            Timestamp startTime) {
        return namedSet.getFilesList().stream().map(f -> OutputArtifactParser.parseArtifact(f, config, startTime))
                .filter(Objects::nonNull).collect(toImmutableList());
    }

    public final String buildId;

    /** A path to the local execroot */
    private final String localExecRoot;

    /** A map from file set ID to file set, with the same ordering as the BEP stream. */
    private final ImmutableMap<String, FileSet> fileSets;
    /** The set of named file sets directly produced by each target. */
    private final SetMultimap<String, String> targetFileSets;

    final Timestamp syncStartTime;

    private final BuildResult buildResult;

    private final long bepBytesConsumed;

    @VisibleForTesting
    public ParsedBepOutput(String buildId, String localExecRoot, ImmutableMap<String, FileSet> fileSets,
            ImmutableSetMultimap<String, String> targetFileSets, Timestamp startTime, BuildResult buildResult,
            long bepBytesConsumed) {
        this.buildId = buildId;
        this.localExecRoot = localExecRoot;
        this.fileSets = fileSets;
        this.targetFileSets = targetFileSets;
        syncStartTime = startTime;
        this.buildResult = buildResult;
        this.bepBytesConsumed = bepBytesConsumed;
    }

    /** Returns all output artifacts of the build. */
    public Set<OutputArtifact> getAllOutputArtifacts(Predicate<String> pathFilter) {
        return fileSets.values().stream().map(s -> s.parsedOutputs).flatMap(List::stream)
                .filter(o -> pathFilter.test(o.getRelativePath())).collect(toImmutableSet());
    }

    public long getBepBytesConsumed() {
        return bepBytesConsumed;
    }

    /** Returns the build result. */
    public BuildResult getBuildResult() {
        return buildResult;
    }

    /** Returns the set of artifacts directly produced by the given target. */
    public ImmutableSet<OutputArtifact> getDirectArtifactsForTarget(Label label, Predicate<String> outputGroupFilter, Predicate<String> pathFilter) {
        return targetFileSets.get(label.toString()).stream().map(s -> fileSets.get(s)).filter(f -> f.outputGroups.stream().anyMatch(outputGroupFilter)).map(f -> f.parsedOutputs)
                .flatMap(List::stream).filter(o -> pathFilter.test(o.getRelativePath())).collect(toImmutableSet());
    }

    /**
     * Returns a map from {@link OutputArtifact#getRelativePath()} to {@link BepArtifactData} for all artifacts reported
     * during the build.
     */
    public ImmutableMap<String, BepArtifactData> getFullArtifactData() {
        return fileSets.values().stream().flatMap(FileSet::toPerArtifactData)
                .collect(toImmutableMap(d -> d.artifact.getRelativePath(), d -> d, BepArtifactData::update));
    }

    /** Returns the local execroot. */
    public String getLocalExecRoot() {
        return localExecRoot;
    }

    public List<OutputArtifact> getOutputGroupArtifacts(Predicate<String> outputGroupFilter) {
        return getOutputGroupArtifacts(outputGroupFilter, s -> true);
    }

    public List<OutputArtifact> getOutputGroupArtifacts(Predicate<String> outputGroupFilter,
            Predicate<String> pathFilter) {
        return fileSets.values().stream().filter(f -> f.outputGroups.stream().anyMatch(outputGroupFilter))
                .map(f -> f.parsedOutputs).flatMap(List::stream).filter(o -> pathFilter.test(o.getRelativePath()))
                .distinct().collect(toImmutableList());
    }

    public List<OutputArtifact> getOutputGroupArtifacts(String outputGroup) {
        return getOutputGroupArtifacts(outputGroup, s -> true);
    }

    public List<OutputArtifact> getOutputGroupArtifacts(String outputGroup, Predicate<String> pathFilter) {
        return fileSets.values().stream().filter(f -> f.outputGroups.contains(outputGroup)).map(f -> f.parsedOutputs)
                .flatMap(List::stream).filter(o -> pathFilter.test(o.getRelativePath())).distinct()
                .collect(toImmutableList());
    }
}