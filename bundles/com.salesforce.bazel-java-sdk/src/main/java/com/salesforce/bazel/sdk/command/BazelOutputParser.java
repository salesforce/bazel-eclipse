/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.sdk.command;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Parses Bazel output.
 */
public class BazelOutputParser {
    // TODO LOGGING TO stdout/err doesnt work here because the command runner output is redirected
    private static final LogHelper LOG = LogHelper.log(BazelOutputParser.class);

    private static enum FailureType {
        BUILD_FILE, JAVA_FILE, UNKNOWN;
    }

    public List<BazelProblem> convertErrorOutputToProblems(List<String> stderrOutputLines) {
        List<BazelProblem> problems = null;

        FailureType failType = assessFailureType(stderrOutputLines);
        switch (failType) {
        case BUILD_FILE:
            problems = parseBuildFileErrorsAsProblems(stderrOutputLines);
            break;
        case JAVA_FILE:
            problems = parseJavaFileErrorsAsProblems(stderrOutputLines);
            break;
        default:
            problems = new ArrayList<>();
            if (stderrOutputLines.size() > 0) {
                // create a generic error entry, hopefully the user can make sense of it
                problems.add(BazelProblem.createError("", 1, stderrOutputLines.get(0)));
            }
            break;
        }

        return problems;
    }

    // GENERAL PURPOSE HELPER UTILS FOR ALL FAILURE TYPES

    private FailureType assessFailureType(List<String> stderrOutputLines) {

        // TODO is there a more robust way to determining the type of package build failure rather than string matches?
        for (String line : stderrOutputLines) {
            if (line.startsWith("ERROR: error loading package")) {
                return FailureType.BUILD_FILE;
            }
            if (line.contains(".java:")) {
                return FailureType.JAVA_FILE;
            }
        }

        return FailureType.UNKNOWN;
    }

    boolean isErrorStatusLine(String line) {
        return line.startsWith("ERROR:");
    }

    boolean isNonErrorStatusLine(String line) {
        return isInfoStatusLine(line) || isFailedStatusLine(line);
    }

    boolean isInfoStatusLine(String line) {
        return line.startsWith("INFO:");
    }

    boolean isFailedStatusLine(String line) {
        return line.startsWith("FAILED:");
    }

    protected String capitalize(String s) {
        if (s.length() > 1) {
            return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
        return s;
    }

    /**
     * Slice a standard error line of the form:
     * 
     * ERROR: [filepath]:[linenumber]:[column]: [description]
     * 
     * and create a BazelProblem and add it to the list.
     */
    public void sliceErrorLine(String line, List<BazelProblem> problemList) {
        // strip 'ERROR: '
        line = line.substring(7);

        // isolate filename
        int colon = line.indexOf(":");
        if (!FSPathHelper.isUnix && colon == 1) {
            // windows path, found the drive discriminator (C:), search again
            colon = line.indexOf(":", colon + 1);
        }
        if (colon == -1) {
            problemList.add(BazelProblem.createError("", 1, line.trim()));
            return;
        }
        String filename = FSPathHelper.osSeps(line.substring(0, colon));

        // isolate line number
        line = line.substring(colon + 1);
        colon = line.indexOf(":");
        if (colon == -1) {
            problemList.add(BazelProblem.createError(filename, 1, line.trim()));
            return;
        }
        String lineNumberStr = line.substring(0, colon);
        int lineNumber = 1;
        try {
            lineNumber = Integer.parseInt(lineNumberStr);
        } catch (Exception anyE) {}

        // isolate description
        line = line.substring(colon + 1);
        colon = line.indexOf(":");
        if (colon != -1) {
            line = line.substring(colon + 1);
        }

        // create the problem
        problemList.add(BazelProblem.createError(filename, lineNumber, line.trim()));

    }

    // BUILD FILE FAILURES

    List<BazelProblem> parseBuildFileErrorsAsProblems(List<String> stderrOutputLines) {
        List<BazelProblem> problems = new ArrayList<>();

        for (String line : stderrOutputLines) {
            parseBuildFileErrorLine(line, problems);
        }
        return problems;
    }

    // ERROR: /Users/plaird/dev/bazel-demo/main_usecases/java/simplejava-mvninstall/projects/libs/apple/apple-api/BUILD:16:5: positional argument may not follow keyword argument
    // ERROR: /Users/plaird/dev/bazel-demo/main_usecases/java/simplejava-mvninstall/projects/libs/apple/apple-api/BUILD:16:5: name 'xyx' is not defined
    // ERROR: error loading package 'projects/libs/apple/apple-api': Package 'projects/libs/apple/apple-api' contains errors

    private void parseBuildFileErrorLine(String line, List<BazelProblem> problemList) {
        if (!isErrorStatusLine(line)) {
            return;
        }
        if (line.startsWith("ERROR: error loading package")) {
            // this is just a summary of the error, the previous lines had the details
            return;
        }

        // parse the error line and create a Problem record
        sliceErrorLine(line, problemList);
    }

    // JAVA SOURCE FILE FAILURES

    // TODO refactor (remove stateful variables) and move this Java error parsing out to a jvm specific package
    private static final String JAVA_FILE_PATH_SUFFX = ".java";
    private boolean haveSkippedFirstLine = false;
    private String errorSourcePathLine = null;
    private String moreDetailsLine = null;

    List<BazelProblem> parseJavaFileErrorsAsProblems(List<String> stderrOutputLines) {
        List<BazelProblem> problems = new ArrayList<>();

        for (String line : stderrOutputLines) {
            parseJavaFileErrorLine(line, problems);
        }
        return problems;
    }

    private void parseJavaFileErrorLine(String line, List<BazelProblem> problemList) {
        line = line.trim();

        // the first error line in a Java file failure output is contains confusing information and should be skipped
        if (haveSkippedFirstLine) {
            if (line.isEmpty()) {
                if (errorSourcePathLine != null) {
                    problemList.add(buildProblemDetailsForJavaError(errorSourcePathLine, moreDetailsLine));
                    errorSourcePathLine = null;
                    moreDetailsLine = null;
                }
            }

            else if (isInitialJavaErrorSourcePathLine(line)) {
                if (errorSourcePathLine == null) {
                    errorSourcePathLine = line;
                } else {
                    problemList.add(buildProblemDetailsForJavaError(errorSourcePathLine, moreDetailsLine));
                    errorSourcePathLine = line;
                    moreDetailsLine = null;
                }

            } else if (isNonErrorStatusLine(line)) {
                haveSkippedFirstLine = false;
                if (errorSourcePathLine != null) {
                    problemList.add(buildProblemDetailsForJavaError(errorSourcePathLine, moreDetailsLine));
                    errorSourcePathLine = null;
                    moreDetailsLine = null;
                }
            } else {
                if (errorSourcePathLine != null) {
                    // already found a line like this: projects/libs/apple/apple-api/src/main/java/demo/apple/api/Apple.java:15: error: ';' expected
                    // the next like may have more details
                    if (moreDetailsLine == null) {
                        moreDetailsLine = line;
                    }
                }
            }
        } else {
            if (isErrorStatusLine(line)) {
                haveSkippedFirstLine = true;
            }
        }

        if (moreDetailsLine != null) {
            problemList.add(buildProblemDetailsForJavaError(errorSourcePathLine, moreDetailsLine));
            errorSourcePathLine = null;
            moreDetailsLine = null;
        }
    }

    private BazelProblem buildProblemDetailsForJavaError(String errorSourcePathLine, String moreDetailsLine) {
        String sourcePath = "";
        int lineNumber = 1;

        // Java Compilation Error looks like this, written on its own line:
        // projects/libs/apple/apple-api/src/main/java/demo/apple/api/Apple.java:55: error: ';' expected

        String description = moreDetailsLine;
        try {
            int i = errorSourcePathLine.lastIndexOf(JAVA_FILE_PATH_SUFFX);
            sourcePath = errorSourcePathLine.substring(0, i + JAVA_FILE_PATH_SUFFX.length());
            i = errorSourcePathLine.indexOf(":", sourcePath.length());
            int j = errorSourcePathLine.indexOf(":", i + 1);
            if (j == -1) {
                j = errorSourcePathLine.length() - 1;
            }
            lineNumber = Integer.parseInt(errorSourcePathLine.substring(i + 1, j));
            description = errorSourcePathLine.substring(j + 1).trim();
            for (String errorPrefix : new String[] { "error", "error:", "ERROR", "ERROR:" }) {
                if (description.startsWith(errorPrefix) && (description.length() > (errorPrefix.length() + 1))) {
                    description = capitalize(description.substring(errorPrefix.length() + 1).trim());
                    break;
                }
            }
            if (moreDetailsLine != null) {
                description += ": " + moreDetailsLine;
            }
        } catch (Exception anyE) {
            // errorSourcePathLine: ERROR: /Users/plaird/dev/myrepo/a/b/c/BUILD:81:1: Target '//a/b/c:src/main/java/com/salesforce/jetty/Foo.java' contains an error and its package is in error and referenced by '//a/b/d:f'
            // moreDetailsLine: null
            LOG.error("Failed to parse line: " + errorSourcePathLine + " with details: " + moreDetailsLine);
            description = "BUILD file error";
        }
        return BazelProblem.createError(sourcePath, lineNumber, description);
    }

    private boolean isInitialJavaErrorSourcePathLine(String line) {
        return line.lastIndexOf(JAVA_FILE_PATH_SUFFX) != -1;
    }

}
