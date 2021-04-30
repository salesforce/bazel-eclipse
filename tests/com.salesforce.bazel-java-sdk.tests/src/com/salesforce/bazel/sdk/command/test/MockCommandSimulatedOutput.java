package com.salesforce.bazel.sdk.command.test;

import java.util.List;

/**
 * Provides the output (output and error lines) from a simulated run of a Bazel command.
 */
public class MockCommandSimulatedOutput {
    public String nameForLog;
    public List<MockCommandSimulatedOutputMatcher> matchers;
    public int matchesRemaining = Integer.MAX_VALUE;
    public List<String> outputLines;
    public List<String> errorLines;

    public MockCommandSimulatedOutput(String nameForLog) {
        this.nameForLog = nameForLog;
    }

    public MockCommandSimulatedOutput(String nameForLog, List<String> outputLines, List<String> errorLines) {
        this.nameForLog = nameForLog;
        this.outputLines = outputLines;
        this.errorLines = errorLines;
    }

    public MockCommandSimulatedOutput(String nameForLog, List<String> outputLines, List<String> errorLines,
            List<MockCommandSimulatedOutputMatcher> matchers) {
        this.nameForLog = nameForLog;
        this.outputLines = outputLines;
        this.errorLines = errorLines;
        this.matchers = matchers;
    }

    /**
     * Determines if this output is to be used for the command.
     */
    public boolean doesMatch(List<String> commandArgs) {
        if (matchesRemaining < 1) {
            // this matcher was configured to be 'consumed' and not reused
            return false;
        }
        if (matchers == null) {
            // test probably is only running one command, so just return true
            matchesRemaining--;
            return true;
        }
        // the matchers must all be true (AND) for the command to match
        for (MockCommandSimulatedOutputMatcher matcher : matchers) {
            if (commandArgs.size() <= matcher.matchArgIndex) {
                // not enough args to match against, so this can't be our command
                return false;
            }
            String pattern = matcher.matchArgRegex;
            String arg = commandArgs.get(matcher.matchArgIndex);
            if (!arg.matches(pattern)) {
                return false;
            }
        }
        matchesRemaining--;
        return true;
    }

}