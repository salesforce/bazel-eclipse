package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.aspects.intellij.IntellijAspects;
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
     * @param outputGroupNames
     *            the output groups to request (use {@link IntellijAspects#getOutputGroupNames(Set, Set, boolean)})
     * @param aspects
     *            the aspects to use
     * @param purpose
     *            a human readable message why the command is needed
     */
    public BazelBuildWithIntelliJAspectsCommand(Path workspaceRoot, List<BazelLabel> targets,
            Collection<String> outputGroupNames, IntellijAspects aspects, BlazeInfo blazeInfo, String purpose) {
        super(targets, workspaceRoot, blazeInfo, true /* keepGoing */, purpose);
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
