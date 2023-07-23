package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.newInputStream;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * <code>bazel build </code> with
 * <code>--build_event_binary_file=bazel_build_bep.txt --nobuild_event_binary_file_path_conversion</code>
 */
public class BazelBuildCommand extends BazelCommand<ParsedBepOutput> {

    private static Logger LOG = LoggerFactory.getLogger(BazelBuildCommand.class);

    private Path bepFile;
    private final boolean keepGoing;
    private final List<BazelLabel> targets;

    public BazelBuildCommand(List<BazelLabel> targets, Path workspaceRoot, boolean keepGoing, String purpose) {
        super("build", workspaceRoot, purpose);
        this.targets = targets;
        this.keepGoing = keepGoing;
    }

    @Override
    protected ParsedBepOutput doGenerateResult() throws IOException {
        throw new IllegalStateException("should not be called");
    }

    @Override
    public ParsedBepOutput generateResult(int exitCode) throws IOException {
        return ParsedBepOutput.parseBepArtifacts(
            newInputStream(
                requireNonNull(
                    bepFile,
                    "unusual code flow; prepareCommandLine not called or overridden incorrectly?")));
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        var commandLine = super.prepareCommandLine(bazelVersion);

        // collect BEP file for parsing output
        bepFile = createTempFile("bazel_build_bep_", ".txt");
        commandLine.add(format("--build_event_binary_file=%s", bepFile));
        LOG.debug("Collecting BEP to: {}", bepFile);

        // instructs BEP to use local file paths (file://...)
        commandLine.add("--nobuild_event_binary_file_path_conversion");

        // keep going
        if (keepGoing) {
            commandLine.add("--keep_going");
        }

        // targets
        for (BazelLabel target : targets) {
            commandLine.add(target.toString());
        }

        return commandLine;
    }

}
