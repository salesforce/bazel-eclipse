package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.newInputStream;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Interner;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * <code>bazel build </code> with
 * <code>--build_event_binary_file=bazel_build_bep.txt --nobuild_event_binary_file_path_conversion</code>
 */
public class BazelBuildCommand extends BazelCommand<ParsedBepOutput> {

    private static Logger LOG = LoggerFactory.getLogger(BazelBuildCommand.class);

    private Path bepFile;
    private Interner<String> interner;
    private final boolean keepGoing;
    private final List<BazelLabel> targets;
    private final BlazeInfo blazeInfo;

    public BazelBuildCommand(List<BazelLabel> targets, Path workspaceRoot, BlazeInfo blazeInfo, boolean keepGoing,
            String purpose) {
        super("build", workspaceRoot, purpose);
        this.targets = targets;
        this.blazeInfo = blazeInfo;
        this.keepGoing = keepGoing;
    }

    @Override
    protected ParsedBepOutput doGenerateResult() throws IOException {
        throw new IllegalStateException("should not be called");
    }

    @Override
    public ParsedBepOutput generateResult(int exitCode) throws IOException {
        try (var in = newInputStream(
            requireNonNull(bepFile, "unusual code flow; prepareCommandLine not called or overridden incorrectly?"))) {
            return ParsedBepOutput.parseBepArtifacts(BuildEventStreamProvider.fromInputStream(in), blazeInfo, interner);
        } finally {
            try {
                if (deleteIfExists(bepFile)) {
                    LOG.debug("Deleted '{}'", bepFile);
                }
            } catch (IOException e) {
                LOG.warn("Unable to delete '{}'. Please clean up manually to free some space.", bepFile, e);
            }
        }
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

    /**
     * @param interner
     *            the interner to use when generating the {@link ParsedBepOutput result}
     */
    public void setInterner(Interner<String> interner) {
        this.interner = interner;
    }

}
