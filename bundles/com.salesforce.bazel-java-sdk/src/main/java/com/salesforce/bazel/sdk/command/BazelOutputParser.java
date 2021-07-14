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

import com.salesforce.bazel.sdk.model.BazelProblem;

/**
 * Parses Bazel output.
 */
public class BazelOutputParser {

    private static final String JAVA_FILE_PATH_SUFFX = ".java";
    private boolean parsingErrors = false;
    private String errorSourcePathLine = null;
    private String moreDetailsLine = null;

    public List<BazelProblem> getErrorBazelMarkerDetails(String latestLine) {
        List<BazelProblem> allBazelMarkerDetails = new ArrayList<>();
        String line = latestLine.trim();
        if (parsingErrors) {
            if (line.isEmpty()) {
                if (errorSourcePathLine != null) {
                    allBazelMarkerDetails.add(buildErrorDetails(errorSourcePathLine, moreDetailsLine));
                    errorSourcePathLine = null;
                    moreDetailsLine = null;
                }
            }

            else if (isInitialErrorSourcePathLine(line)) {
                if (errorSourcePathLine == null) {
                    errorSourcePathLine = line;
                } else {
                    allBazelMarkerDetails.add(buildErrorDetails(errorSourcePathLine, moreDetailsLine));
                    errorSourcePathLine = line;
                    moreDetailsLine = null;
                }

            } else if (isNonErrorStatusLine(line)) {
                parsingErrors = false;
                if (errorSourcePathLine != null) {
                    allBazelMarkerDetails.add(buildErrorDetails(errorSourcePathLine, moreDetailsLine));
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
                parsingErrors = true;
            }
        }

        if (moreDetailsLine != null) {
            allBazelMarkerDetails.add(buildErrorDetails(errorSourcePathLine, moreDetailsLine));
            errorSourcePathLine = null;
            moreDetailsLine = null;
        }

        return allBazelMarkerDetails;
    }

    public List<BazelProblem> getErrors(List<String> lines) {
        parsingErrors = false;
        errorSourcePathLine = null;
        moreDetailsLine = null;
        List<BazelProblem> errors = new ArrayList<>();
        for (String line : lines) {
            List<BazelProblem> bazelMarkerDetails = getErrorBazelMarkerDetails(line);
            errors.addAll(bazelMarkerDetails);
        }
        return errors;
    }

    private BazelProblem buildErrorDetails(String errorSourcePathLine, String moreDetailsLine) {
        String sourcePath = "";
        int lineNumber = 1;
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
            // BUILD file update error TODO
            // errorSourcePathLine: ERROR: /Users/plaird/dev/myrepo/a/b/c/BUILD:81:1: Target '//a/b/c:src/main/java/com/salesforce/jetty/Foo.java' contains an error and its package is in error and referenced by '//a/b/d:f'
            // moreDetailsLine: null
            System.err.println("Failed to parse line: " + errorSourcePathLine + " with details: " + moreDetailsLine);
            description = "BUILD file error";
        }
        return BazelProblem.createError(sourcePath, lineNumber, description);
    }

    private boolean isInitialErrorSourcePathLine(String line) {
        return line.lastIndexOf(JAVA_FILE_PATH_SUFFX) != -1;
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

    private static String capitalize(String s) {
        if (s.length() > 1) {
            return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
        return s;
    }

}
