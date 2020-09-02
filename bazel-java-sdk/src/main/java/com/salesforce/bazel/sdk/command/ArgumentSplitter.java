package com.salesforce.bazel.sdk.command;

import java.util.ArrayList;
import java.util.List;

public class ArgumentSplitter {

    public List<String> split(String argumentsString) {
        // this should probably be more sophisticated, at least handle quotes...
        List<String> args = new ArrayList<>();
        for (String arg : argumentsString.split("\\s+")) {
            arg = arg.trim();
            if (arg.length() > 0) {
                args.add(arg);
            }
        }
        return args;
    }
}
