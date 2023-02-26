package com.salesforce.bazel.sdk.command;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.newInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;

/**
 * <code>bazel query --output proto</code>
 */
public class BazelQueryForTargetProtoCommand extends BazelQueryCommand<Collection<Build.Target>> {

    public BazelQueryForTargetProtoCommand(Path workspaceRoot, String query, boolean keepGoing) {
        super(workspaceRoot, query, keepGoing);
        setCommandArgs("--output", "proto", "--order_output=no");
    }

    @Override
    public List<String> prepareCommandLine() throws IOException {
        // redirect output to file for parsing
        var stdoutFile = createTempFile("bazel_query_stdout_", ".bin");
        setRedirectStdOutToFile(stdoutFile);

        // prepare regular query command line
        return super.prepareCommandLine();
    }

    @Override
    protected Collection<Target> processResult(int exitCode, String stdout, String stderr) throws IOException {
        List<Target> result = new ArrayList<>();
        try (InputStream in = newInputStream(getStdOutFile())) {
            Target target;
            do {
                target = Target.parseDelimitedFrom(in);
                result.add(target);
            } while (target != null);
        }
        return result;
    }
}
