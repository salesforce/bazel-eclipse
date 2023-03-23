package com.salesforce.bazel.sdk.command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * <code>bazel info</code>
 */
public class BazelInfoCommand extends BazelCommand<Map<String, String>> {

    public BazelInfoCommand(Path workspaceRoot) {
        super("info", workspaceRoot);
    }

    @Override
    public Map<String, String> processResult(int exitCode, String stdout, String stderr) throws IOException {
        HashMap<String, String> result = new HashMap<>();

        StringTokenizer linesTokenizer = new StringTokenizer(stdout, System.lineSeparator());
        while(linesTokenizer.hasMoreTokens()) {
            String line = linesTokenizer.nextToken();
            int separatorPos = line.indexOf(':');
            if(separatorPos > 0) {
                String key = line.substring(0, separatorPos).strip();
                String value = line.length() > separatorPos ? line.substring(separatorPos+1).strip() : null;
                result.put(key, value);
            }
        }

        return result;
    }
}
