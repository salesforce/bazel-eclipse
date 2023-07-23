package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects.OutputGroup;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * <code>bazel build --aspects=@intellij_aspect//:intellij_info_bundled.bzl%intellij_info_aspect --output_groups=...</code>
 * <p>
 * Runs a Bazel build with IntelliJ aspects configured and collects any information from the BuildEventStream
 * </p>
 */
public class BazelBuildWithIntelliJAspectsCommand extends BazelBuildCommand {

    private final IntellijAspects aspects;
    private final Collection<String> outputGroupNames;

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
     * @param purpose
     */
    public BazelBuildWithIntelliJAspectsCommand(Path workspaceRoot, List<BazelLabel> targets,
            Set<OutputGroup> outputGroups, IntellijAspects aspects, Set<LanguageClass> languages,
            boolean onlyDirectDeps, String purpose) {
        super(targets, workspaceRoot, true /* keepGoing */, purpose);
        this.aspects = aspects;
        this.outputGroupNames = aspects.getOutputGroupNames(outputGroups, languages, onlyDirectDeps);
    }

    public BazelBuildWithIntelliJAspectsCommand(Path workspaceRoot, List<BazelLabel> targets,
            Set<String> outputGroupNames, IntellijAspects aspects, String purpose) {
        super(targets, workspaceRoot, true /* keepGoing */, purpose);
        this.aspects = aspects;
        this.outputGroupNames = outputGroupNames;
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        var commandLine = super.prepareCommandLine(bazelVersion);

        // enable aspects and request output groups
        commandLine.addAll(aspects.getFlags(bazelVersion));
        commandLine.add(format("--output_groups=%s", outputGroupNames.stream().collect(joining(","))));

        return commandLine;
    }
}
