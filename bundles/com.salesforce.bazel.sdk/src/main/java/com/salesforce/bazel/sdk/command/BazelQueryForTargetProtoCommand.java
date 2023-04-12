package com.salesforce.bazel.sdk.command;

import static java.lang.String.format;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.newInputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.salesforce.bazel.sdk.BazelVersion;

/**
 * <code>bazel query --output streamed_proto --order_output=no</code>
 */
public class BazelQueryForTargetProtoCommand extends BazelQueryCommand<Collection<Build.Target>> {

    public BazelQueryForTargetProtoCommand(Path workspaceRoot, String query, boolean keepGoing) {
        super(workspaceRoot, query, keepGoing);
        setCommandArgs("--output", "streamed_proto", "--order_output=no");
    }

    @Override
    public Collection<Target> generateResult(int exitCode) throws IOException {
        if (exitCode != 0) {
            throw new IOException(
                    format("Bazel query command failed with exit code %d. Please check command output", exitCode));
        }

        List<Target> result = new ArrayList<>();
        try (var in = newInputStream(getStdOutFile())) {
            Target target;
            do {
                target = Target.parseDelimitedFrom(in);
                if (target != null) {
                    result.add(target);
                }
            } while (target != null);
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
