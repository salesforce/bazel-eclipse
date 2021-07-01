package com.salesforce.bazel.sdk.command.test;

import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Filter that enables Bazel command output to be associated with a particular command. By providing a list of these,
 * you can make sure that the output lines are only returned to the caller if all matchers return true.
 */
public class MockCommandSimulatedOutputMatcher {
    public int matchArgIndex;
    public String matchArgRegex;

    public MockCommandSimulatedOutputMatcher(int index, String regex) {
        matchArgIndex = index;
        matchArgRegex = regex;

        if (matchArgRegex.contains(FSPathHelper.WINDOWS_BACKSLASH)) {
            matchArgRegex =
                    matchArgRegex.replace(FSPathHelper.WINDOWS_BACKSLASH, FSPathHelper.WINDOWS_BACKSLASH_REGEX);
        }
    }
}