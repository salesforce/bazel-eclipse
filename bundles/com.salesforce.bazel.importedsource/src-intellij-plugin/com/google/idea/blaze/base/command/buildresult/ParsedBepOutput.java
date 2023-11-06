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
import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
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
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.TargetComplete;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.NestedSetVisitor;
import com.google.devtools.build.lib.collect.nestedset.NestedSetVisitor.Receiver;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.command.info.BlazeInfo;
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

            FileSet build(Map<String, String> configIdToMnemonic, Timestamp startTime, BlazeInfo blazeInfo) {
                ImmutableList<OutputArtifact> parsedOutputs = parseFiles(namedSet, configIdToMnemonic.get(configId), startTime, blazeInfo);
				return new FileSet(parsedOutputs,  outputGroups, targets);
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

        FileSet(List<OutputArtifact> parsedOutputs, Set<String> outputGroups,
                Set<String> targets) {
            this.parsedOutputs = ImmutableList.copyOf(parsedOutputs);
            this.outputGroups = ImmutableSet.copyOf(outputGroups);
            this.targets = ImmutableSet.copyOf(targets);
        }

        private Stream<BepArtifactData> toPerArtifactData() {
            return parsedOutputs.stream().map(a -> new BepArtifactData(a, outputGroups, targets));
        }
    }

    private static class CompletedTarget {
        private final ImmutableMap<String, List<String>> fileSetsByOutputGroup;
		private final String label;
		private final String configId;

        final Map<String, NestedSet<OutputArtifact>> outputFilesByOutputGroup = new HashMap<>();
        String configMnemonic;

        CompletedTarget(String label, String configId, Map<String, List<String>> fileSetsByOutputGroup) {
            this.label = label;
			this.configId = configId;
			this.fileSetsByOutputGroup = ImmutableMap.copyOf(fileSetsByOutputGroup);
        }

		public void putOutputFiles(String outputGroup, NestedSet<OutputArtifact> outputFiles) {
			outputFilesByOutputGroup.put(outputGroup, outputFiles);
		}
    }

    /**
     * Only top-level targets have configuration mnemonic, producing target, and output group data explicitly provided
     * in BEP. This method fills in that data for the transitive closure.
     * @param blazeInfo
     */
    private static ImmutableMap<String, FileSet> fillInTransitiveFileSetData(Map<String, FileSet.Builder> fileSets,
            Set<String> topLevelFileSets, Map<String, String> configIdToMnemonic, Timestamp startTime, BlazeInfo blazeInfo) {
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
                    });
            visited.add(setId);
        }
        return fileSets.entrySet().stream().filter(e -> e.getValue().isValid(configIdToMnemonic))
                .collect(toImmutableMap(Entry::getKey, e -> e.getValue().build(configIdToMnemonic, startTime, blazeInfo)));
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
    public static ParsedBepOutput parseBepArtifacts(BuildEventStreamProvider stream, BlazeInfo blazeInfo) throws BuildEventStreamException {
        return parseBepArtifacts(stream, blazeInfo, null);
    }

    /**
     * Parses BEP events into {@link ParsedBepOutput}. String references in {@link NamedSetOfFiles} are interned to
     * conserve memory.
     *
     * <p>
     * BEP protos often contain many duplicate strings both within a single stream and across shards running in
     * parallel, so a {@link Interner} is used to share references.
     */
    public static ParsedBepOutput parseBepArtifacts(BuildEventStreamProvider stream, BlazeInfo blazeInfo, Interner<String> interner)
            throws BuildEventStreamException {

        if (interner == null) {
            interner = Interners.newStrongInterner();
        }

        BuildEvent event;
        Map<String, String> configIdToMnemonic = new HashMap<>();
        Map<String, NamedSetOfFiles> namedSetOfFilesById = new LinkedHashMap<>();
        List<CompletedTarget> topLevelTargets = new ArrayList<>();
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
                    configIdToMnemonic.put(interner.intern(event.getId().getConfiguration().getId()),
                        interner.intern(event.getConfiguration().getMnemonic()));
                    continue;
                case NAMED_SET:
                    var namedSet = internNamedSet(event.getNamedSetOfFiles(), interner);
                    String fileSetId = event.getId().getNamedSet().getId();
                    if(null != namedSetOfFilesById.putIfAbsent(fileSetId, namedSet))
                    	throw new BuildEventStreamException("Unexpected duplicate file set: " +namedSetOfFilesById);
                    continue;
                case TARGET_COMPLETED:
                    var label = interner.intern(event.getId().getTargetCompleted().getLabel());
                    var configId = interner.intern(event.getId().getTargetCompleted().getConfiguration().getId());

                    var outputs = ImmutableMap.<String, List<String>> builder();
                    for (OutputGroup o : event.getCompleted().getOutputGroupList()) {
                    	var sets = getFileSets(o);
                    	outputs.put(interner.intern(o.getName()), sets);
					}
                    topLevelTargets.add(new CompletedTarget(label, configId, outputs.build()));
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

        // now that we got everything we can build the graph and resolve all things for each target
        var filesGraph = new HashMap<String, NestedSet<OutputArtifact>>();
        for (CompletedTarget completedTarget : topLevelTargets) {
        	completedTarget.configMnemonic = configIdToMnemonic.get(completedTarget.configId);
        	var timestamp = startTime;
        	for (Entry<String, List<String>> outputGroupAndFileSets : completedTarget.fileSetsByOutputGroup.entrySet()) {
        		var fileset = NestedSetBuilder.<OutputArtifact>stableOrder();
        		String outputGroup = outputGroupAndFileSets.getKey();
        		for (String fileSetId : outputGroupAndFileSets.getValue()) {
        			fileset.addTransitive(computeOutputFiles(blazeInfo, namedSetOfFilesById, filesGraph,
        					completedTarget.configMnemonic, timestamp, fileSetId));
				};
				completedTarget.putOutputFiles(outputGroup, fileset.build());
			}
		}
        return new ParsedBepOutput(buildId, localExecRoot, topLevelTargets, startTime, buildResult,
                stream.getBytesConsumed());
    }

	private static NestedSet<OutputArtifact> computeOutputFiles(BlazeInfo blazeInfo,
			Map<String, NamedSetOfFiles> namedSetOfFilesById,
			HashMap<String, NestedSet<OutputArtifact>> filesGraph,
			String configMnemonic, Timestamp timestamp, String fileSetId) {
		String graphKey = getGraphKey(configMnemonic, fileSetId);
		NestedSet<OutputArtifact> nestedSet = filesGraph.get(graphKey);
		if(nestedSet != null)
			return nestedSet;

		NestedSetBuilder<OutputArtifact> builder = NestedSetBuilder.stableOrder();
		NamedSetOfFiles fileSet = requireNonNull(namedSetOfFilesById.get(fileSetId), "Expected NamedFileSet not found: " + fileSetId);
		ImmutableList<OutputArtifact> files = parseFiles(fileSet, configMnemonic, timestamp, blazeInfo);
		builder.addAll(files);

		for (NamedSetOfFilesId referencedFileSets : fileSet.getFileSetsList()) {
			NestedSet<OutputArtifact> referencedSet = computeOutputFiles(blazeInfo, namedSetOfFilesById, filesGraph, configMnemonic, timestamp, referencedFileSets.getId());
			builder.addTransitive(referencedSet);
		}

		nestedSet = builder.build();
		filesGraph.put(graphKey, nestedSet);
		return nestedSet;
	}

	private static String getGraphKey(String configMnemonic, String fileSetId) {
		return fileSetId + ":" + configMnemonic;
	}

    /** Parses BEP events into {@link ParsedBepOutput} */
    public static ParsedBepOutput parseBepArtifacts(InputStream bepStream, BlazeInfo blazeInfo) throws BuildEventStreamException {
        return parseBepArtifacts(BuildEventStreamProvider.fromInputStream(bepStream), blazeInfo);
    }

    private static ImmutableList<OutputArtifact> parseFiles(NamedSetOfFiles namedSet, String config,
            Timestamp startTime, BlazeInfo blazeInfo) {
        return namedSet.getFilesList().stream().map(f -> OutputArtifactParser.parseArtifact(f, config, startTime, blazeInfo))
                .filter(Objects::nonNull).collect(toImmutableList());
    }

    public final String buildId;

    /** A path to the local execroot */
    private final String localExecRoot;

    private final Timestamp syncStartTime;
    private final BuildResult buildResult;
    private final long bepBytesConsumed;
	private final ImmutableList<CompletedTarget> topLevelTargets;

    @VisibleForTesting
    public ParsedBepOutput(String buildId, String localExecRoot, List<CompletedTarget> topLevelTargets, Timestamp startTime, BuildResult buildResult,
            long bepBytesConsumed) {
        this.buildId = buildId;
        this.localExecRoot = localExecRoot;
        this.topLevelTargets = ImmutableList.copyOf(topLevelTargets);
        syncStartTime = startTime;
        this.buildResult = buildResult;
        this.bepBytesConsumed = bepBytesConsumed;
    }

    public long getBepBytesConsumed() {
        return bepBytesConsumed;
    }

    /** Returns the build result. */
    public BuildResult getBuildResult() {
        return buildResult;
    }

    /** Returns the local execroot. */
    public String getLocalExecRoot() {
        return localExecRoot;
    }

    public Collection<OutputArtifact> getOutputGroupArtifacts(Predicate<String> outputGroupFilter,
            Predicate<String> pathFilter) {
		CopyOnWriteArraySet<OutputArtifact> result = new CopyOnWriteArraySet<>();
		NestedSetVisitor<OutputArtifact> collector = new NestedSetVisitor<OutputArtifact>(
				a -> {
					if (pathFilter.test(a.getRelativePath()))
						result.add(a);
				}, new NestedSetVisitor.VisitedState<>());
		topLevelTargets.parallelStream()
				.flatMap(t -> t.outputFilesByOutputGroup.entrySet()
						.parallelStream())
				.filter(e -> outputGroupFilter.test(e.getKey()))
				.map(Entry::getValue).forEach(set -> {
					try {
						collector.visit(set);
					} catch (InterruptedException e) {
						throw new IllegalStateException("interrupted", e);
					}
				});
		return result;
    }

    public NestedSet<OutputArtifact> getOutputGroupArtifacts(String outputGroup) {
		NestedSetBuilder<OutputArtifact> builder = NestedSetBuilder.stableOrder();
		for (CompletedTarget target : topLevelTargets) {
			NestedSet<OutputArtifact> set = target.outputFilesByOutputGroup.get(outputGroup);
			if(set!=null)
				builder.addTransitive(set);
		}
        return builder.build();
    }

    public NestedSet<OutputArtifact> getOutputGroupArtifacts(Label targetLabel, String outputGroup) {
		NestedSetBuilder<OutputArtifact> builder = NestedSetBuilder.stableOrder();
		for (CompletedTarget target : topLevelTargets) {
			if(targetLabel.toString().equals(target.label)) {
				NestedSet<OutputArtifact> set = target.outputFilesByOutputGroup.get(outputGroup);
				if(set!=null)
					builder.addTransitive(set);
			}
		}
        return builder.build();
    }
}