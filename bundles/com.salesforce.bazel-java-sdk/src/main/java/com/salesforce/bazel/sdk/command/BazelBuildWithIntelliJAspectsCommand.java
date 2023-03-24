package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.newInputStream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.aspect.IntellijAspects;
import com.salesforce.bazel.sdk.aspect.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.command.buildresults.ParsedBepOutput;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.primitives.LanguageClass;

/**
 * <code>bazel build</code>
 * <p>
 * Runs a Bazel build with IntelliJ aspects configured and collects any information from the BuildEventStream
 * </p>
 */
public class BazelBuildWithIntelliJAspectsCommand extends BazelCommand<ParsedBepOutput> {

    private final IntellijAspects aspects;
    private final Set<LanguageClass> languages;
    private final Set<OutputGroup> outputGroups;
    private Path bepFile;
    private boolean onlyDirectDeps;
    private List<BazelLabel> targets;

    /**
     * @param workspaceRoot
     *            the Bazel workspace root
     * @param targets
     *            the targets to build
     * @param outputGroups
     *            the output groups to request (usually {@link OutputGroup#INFO} and {@link OutputGroup#RESOLVE}
     *            together or only {@link OutputGroup#COMPILE})
     * @param aspects
     *            the aspects to use
     * @param languages
     *            the set of languages to obtain information for (as configured in the project view)
     * @param onlyDirectDeps
     *            should be set to <code>true</code> when the project view specifies
     *            <code>derive_targets_from_directories</code> (see
     *            https://github.com/bazelbuild/intellij/blob/37813e607ad26716c4d1ccf4bc3e7163b2188658/base/src/com/google/idea/blaze/base/sync/aspects/BlazeIdeInterfaceAspectsImpl.java#L724)
     */
    public BazelBuildWithIntelliJAspectsCommand(Path workspaceRoot, List<BazelLabel> targets, Set<OutputGroup> outputGroups,
            IntellijAspects aspects, Set<LanguageClass> languages, boolean onlyDirectDeps) {
        super("build", workspaceRoot);
        this.targets = targets;
        this.outputGroups = outputGroups;
        this.aspects = aspects;
        this.languages = languages;
        this.onlyDirectDeps = onlyDirectDeps;
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        List<String> commandLine = super.prepareCommandLine(bazelVersion);

        // enable aspects and request output groups
        commandLine.addAll(aspects.getFlags(bazelVersion));
        commandLine.add(format("--output_groups=%s", aspects.getOutputGroupNames(outputGroups, languages, onlyDirectDeps).stream().collect(joining(","))));

        // collect BEP file for parsing output
        bepFile = createTempFile("bazel_build_bep_", ".txt");
        commandLine.add(format("--build_event_binary_file=%s", bepFile));

        // instructs BEP to use local file paths (file://...)
        commandLine.add("--nobuild_event_binary_file_path_conversion");

        // keep going
        commandLine.add("--keep_going");

        // targets
        for (BazelLabel target : targets) {
            commandLine.add(target.toString());
        }

        return commandLine;
    }

    @Override
    public ParsedBepOutput generateResult(int exitCode) throws IOException {
        return ParsedBepOutput.parseBepArtifacts(newInputStream(requireNonNull(bepFile, "unusual code flow; prepareCommandLine not called or overridden incorrectly?")));
    }
}
