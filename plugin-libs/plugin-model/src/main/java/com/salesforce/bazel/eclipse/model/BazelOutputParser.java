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
package com.salesforce.bazel.eclipse.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Bazel output.
 * 
 * @author stoens
 * @since the real summer of 2019
 */
public class BazelOutputParser {

    private static final String JAVA_FILE_PATH_SUFFX = ".java";
    private boolean parsingErrors = false;
    private String errorSourcePathLine = null;
    private String moreDetailsLine = null;

    public List<BazelProblem> getErrorBazelMarkerDetails(String latestLine) {
        List<BazelProblem> allBazelMarkerDetails = new ArrayList<>();
        String line = latestLine.trim();
        if (this.parsingErrors) {
            if (line.isEmpty()) {
                if (this.errorSourcePathLine != null) {
                    allBazelMarkerDetails.add(buildErrorDetails(this.errorSourcePathLine, this.moreDetailsLine));
                    this.errorSourcePathLine = null;
                    this.moreDetailsLine = null;
                }
            }
            
            else if (isInitialErrorSourcePathLine(line)) {
                if (this.errorSourcePathLine == null) {
                    this.errorSourcePathLine = line;
                } else {
                    allBazelMarkerDetails.add(buildErrorDetails(this.errorSourcePathLine, this.moreDetailsLine));
                    this.errorSourcePathLine = line;
                    this.moreDetailsLine = null;
                }

            } else if (isNonErrorStatusLine(line)) {
                this.parsingErrors = false;
                if (this.errorSourcePathLine != null) {
                    allBazelMarkerDetails.add(buildErrorDetails(this.errorSourcePathLine, this.moreDetailsLine));
                    this.errorSourcePathLine = null;
                    this.moreDetailsLine = null;
                }
            } else {
                if (this.errorSourcePathLine != null) {
                    // already found a line like this: projects/libs/apple/apple-api/src/main/java/demo/apple/api/Apple.java:15: error: ';' expected
                    // the next like may have more details
                    if (this.moreDetailsLine == null) {
                        this.moreDetailsLine = line;
                    }
                }
            }
        } else {
            if (isErrorStatusLine(line)) {
                this.parsingErrors = true;
            }
        }
        
        if (this.moreDetailsLine != null) {
            allBazelMarkerDetails.add(buildErrorDetails(this.errorSourcePathLine, this.moreDetailsLine));
            this.errorSourcePathLine = null;
            this.moreDetailsLine = null;
        }
        
        return allBazelMarkerDetails;
    }
    
    public List<BazelProblem> getErrorBazelMarkerDetails(List<String> lines) {
        this.parsingErrors = false;
        this.errorSourcePathLine = null;
        this.moreDetailsLine = null;
        List<BazelProblem> allBazelMarkerDetails = new ArrayList<>();
        for (String line : lines) {
            List<BazelProblem> bazelMarkerDetails = getErrorBazelMarkerDetails(line);
            allBazelMarkerDetails.addAll(bazelMarkerDetails);
        }

        return allBazelMarkerDetails;
    }

    private BazelProblem buildErrorDetails(String errorSourcePathLine, String moreDetailsLine) {
        int i = errorSourcePathLine.lastIndexOf(JAVA_FILE_PATH_SUFFX);
        String sourcePath = errorSourcePathLine.substring(0, i + JAVA_FILE_PATH_SUFFX.length());
        i = errorSourcePathLine.indexOf(":", sourcePath.length());
        int j = errorSourcePathLine.indexOf(":", i + 1);
        int lineNumber = Integer.parseInt(errorSourcePathLine.substring(i + 1, j));
        String description = errorSourcePathLine.substring(j + 1).trim();
        for (String errorPrefix : new String[] { "error", "error:", "ERROR", "ERROR:" }) {
            if (description.startsWith(errorPrefix) && description.length() > errorPrefix.length() + 1) {
                description = capitalize(description.substring(errorPrefix.length() + 1).trim());
                break;
            }
        }
        if (moreDetailsLine != null) {
            description += ": " + moreDetailsLine;
        }
        return new BazelProblem(sourcePath, lineNumber, description);
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
