package com.salesforce.bazel.sdk.command;

import static java.io.File.createTempFile;
import static java.nio.file.Files.readAllLines;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.BazelVersion;

/**
 * <code>bazel info</code>
 */
public class BazelInfoCommand extends BazelCommand<Map<String, String>> {

    public BazelInfoCommand(Path workspaceRoot) {
        super("info", workspaceRoot);
    }

    @Override
    public Map<String, String> generateResult(int exitCode) throws IOException {
        var result = new HashMap<String, String>();

        var lines = readAllLines(getStdOutFile());
        for (String line : lines) {
            var separatorPos = line.indexOf(':');
            if (separatorPos > 0) {
                var key = line.substring(0, separatorPos).strip();
                var value = line.length() > separatorPos ? line.substring(separatorPos + 1).strip() : null;
                result.put(key, value);
            }
        }

        return result;
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        setRedirectStdOutToFile(createTempFile("bazel_info_", ".txt").toPath());
        return super.prepareCommandLine(bazelVersion);
    }
}
