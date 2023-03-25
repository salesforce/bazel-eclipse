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
package com.salesforce.bazel.sdk.bep.event;

import java.util.List;

import org.json.simple.JSONObject;

/**
 * Model for the Build Progress BEP event.
 */
public class BEPProgressEvent extends BEPEvent {

    public static final String NAME = "progress";
    private static boolean includeStdOutErrInToString = true;

    /**
     * Calling toString() on a progress event can be very verbose if the default behavior of including stdout and stderr
     * in the output. You may enable/disable this behavior.
     */
    public static void includeStdOutErrInToString(boolean include) {
        includeStdOutErrInToString = include;
    }

    protected List<String> stdout;

    protected List<String> stderr;

    // GETTERS

    public BEPProgressEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        var progressDetail = (JSONObject) eventObj.get("progress");
        if (progressDetail != null) {
            parseDetails(progressDetail);
        }
    }

    public List<String> getStderr() {
        return stderr;
    }

    // FEATURE TOGGLES

    public List<String> getStdout() {
        return stdout;
    }

    // PARSER

    void parseDetails(JSONObject progressDetail) {

        // TODO defer the heavy work of cleaning and deduping lines
        // TODO should we be doing the error detection at all, and can we defer it if we need to do a text scan

        var stderrObj = progressDetail.get("stderr");
        if (stderrObj != null) {
            var stderrStr = stderrObj.toString();
            if (stderrStr.startsWith("ERROR:") || stderrStr.contains("FAILED")) {
                isError = true;
            }
            stderr = splitAndCleanAndDedupeLines(stderrStr);
        }
        var stdoutObj = progressDetail.get("stdout");
        if (stdoutObj != null) {
            var stdoutStr = stdoutObj.toString();
            stdout = splitAndCleanAndDedupeLines(stdoutStr);
        }
    }

    // TOSTRING

    @Override
    public String toString() {
        var stdoutStr = includeStdOutErrInToString ? "stdout=" + stdout.toString() : "";
        var stderrStr = includeStdOutErrInToString ? ", stderr=" + stderr.toString() + ", " : "";
        return "BEPProgressEvent [" + stdoutStr + stderrStr + "index=" + index + ", eventType=" + eventType
                + ", isProcessed=" + isProcessed + ", isLastMessage=" + isLastMessage + ", isError=" + isError + "]";
    }
}
