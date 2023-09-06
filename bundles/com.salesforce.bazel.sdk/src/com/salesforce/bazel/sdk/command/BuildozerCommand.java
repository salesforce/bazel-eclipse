/*-
 *
 */
package com.salesforce.bazel.sdk.command;

import static java.io.File.createTempFile;
import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.write;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.buildozer.BuildozerProtos.Output;
import com.salesforce.bazel.sdk.BazelVersion;

/**
 * A specialized command to execute <code>buildozer</code>.
 */
public class BuildozerCommand extends BazelCommand<List<Output>> {

    private static final String BUILDOZER = "buildozer";
    private static final Path DEFAULT_BUILDOZER_EXECUTABLE = Path.of(BUILDOZER);

    private static Logger LOG = LoggerFactory.getLogger(BuildozerCommand.class);

    private final List<String> buildozerCommands;
    private final List<String> targets;

    /**
     * Creates a new command
     *
     * @param workspaceRoot
     *            typically the workspace root
     * @param buildozerCommands
     *            the list of buildozer commands (will be written to a file and passed to buildozer using
     *            <code>-f FILE</code>)
     * @param targets
     *            the list of targets (see buildozer doc for possibilities)
     * @param purpose
     *            a human readable description
     */
    public BuildozerCommand(Path workspaceRoot, List<String> buildozerCommands, List<String> targets, String purpose) {
        super(BUILDOZER, workspaceRoot, purpose);
        this.buildozerCommands = buildozerCommands;
        this.targets = targets;
    }

    @Override
    protected List<Output> doGenerateResult() throws IOException {
        List<Output> result = new ArrayList<>();
        try (var in = newInputStream(getStdOutFile())) {
            Output output;
            do {
                output = Output.parseDelimitedFrom(in);
                if (output != null) {
                    result.add(output);
                }
            } while (output != null);
        }
        return result;
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        // we intentionally don't call super but override it to drop anything from the base class
        var commandLine = new ArrayList<String>();

        setRedirectStdOutToFile(createTempFile("buildozer_", ".bin").toPath());
        commandLine.add("-output_proto");

        if ((buildozerCommands.size() == 1) && (buildozerCommands.get(0).indexOf('\'') == -1)) {
            // no single quote and just one line so let's add the command directly
            commandLine.add(format("'%s'", buildozerCommands.get(0)));
        } else {
            // too complicated, use a file
            var commandsFile = createTempFile("buildozer_commands_", ".txt").toPath();
            write(commandsFile, buildozerCommands);
            commandLine.add("-f");
            commandLine.add(commandsFile.toString());
        }

        commandLine.addAll(targets);

        return commandLine;
    }

    @Override
    public void setBazelBinary(BazelBinary bazelBinary) {
        // override with buildozer executable but keep the Bazel version
        var buildozerExecutable = bazelBinary.executable().resolveSibling(BUILDOZER);
        if (!DEFAULT_BUILDOZER_EXECUTABLE.equals(buildozerExecutable)) {
            if (!buildozerExecutable.isAbsolute()) {
                // try resolving it from the working directory
                var maybeExecutable = getWorkingDirectory().resolve(buildozerExecutable);
                if (isRegularFile(maybeExecutable)) {
                    buildozerExecutable = getWorkingDirectory().resolve(buildozerExecutable);
                } else {
                    // leave it to the shell to discover it
                    LOG.debug("Unable to resolve '{}', relying on shell to do it!", buildozerExecutable);
                }
            } else if (!isRegularFile(buildozerExecutable)) {
                // check if it actually exists or we are resolving to something invalid
                // log a warning in any case so user can correct if necessary
                LOG.warn(
                    "Buildozer executable '{}' does not exist, falling back to default from environment.",
                    buildozerExecutable);
                buildozerExecutable = DEFAULT_BUILDOZER_EXECUTABLE;
            }
        }

        LOG.debug("Using buildozer binary: {}", buildozerExecutable);
        super.setBazelBinary(new BazelBinary(buildozerExecutable, bazelBinary.bazelVersion()));
    }

    @Override
    boolean supportsInjectionOfAdditionalBazelOptions() {
        return false; // buildozer doesn't support --tool_tag & co
    }

}
