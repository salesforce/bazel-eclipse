package com.salesforce.bazel.sdk.command;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.newInputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.querylight.Target;

/**
 * <code>bazel query --output streamed_proto --order_output=no</code>
 */
public class BazelQueryForTargetProtoCommand extends BazelQueryCommand<Collection<Target>> {

    private static Logger LOG = LoggerFactory.getLogger(BazelQueryForTargetProtoCommand.class);

    public BazelQueryForTargetProtoCommand(Path workspaceRoot, String query, boolean keepGoing,
            List<String> additionalProtoArgs, String purpose) {
        super(workspaceRoot, query, keepGoing, purpose);

        List<String> commandArgs = new ArrayList<>();
        commandArgs.add("--output");
        commandArgs.add("streamed_proto");
        commandArgs.add("--order_output=no");
        commandArgs.addAll(additionalProtoArgs);
        setCommandArgs(commandArgs);
    }

    @Override
    protected Collection<Target> doGenerateResult() throws IOException {
        List<Target> result = new ArrayList<>();
        try (var in = newInputStream(getStdOutFile())) {
            Build.Target target;
            do {
                target = Build.Target.parseDelimitedFrom(in);
                if (target != null) {
                    result.add(new Target(target));
                }
            } while (target != null);
        } finally {
            try {
                deleteIfExists(getStdOutFile());
            } catch (IOException e) {
                LOG.warn("Error deleting '{}'. Please delete manually to save some space.", getStdOutFile(), e);
            }
        }
        return result;
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        // redirect output to file for parsing
        var stdoutFile = createTempFile("bazel_query_stdout_", ".bin");
        setRedirectStdOutToFile(stdoutFile);

        // prepare regular query command line
        return super.prepareCommandLine(bazelVersion);
    }
}
