/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
            try {
                if (!arg.matches(pattern)) {
                    return false;
                }
            } catch (Exception anyE) {
                // bug in the test framework
                anyE.printStackTrace();
                throw anyE;
            }
        }
        matchesRemaining--;
        return true;
    }

}