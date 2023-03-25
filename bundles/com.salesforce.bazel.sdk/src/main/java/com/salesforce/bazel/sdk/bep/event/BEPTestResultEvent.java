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

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Model for the Test Result BEP event.
 * <p>
 * The most common use of this event is to detect and inspect test failures. For this case, follow this approach:
 * <ul>
 * <li>If the test failed, isError() will be true</li>
 * <li>The detailed error messages can be retrieved by reading in the test log file using getActionOutputs().</li>
 * <li>From the list of action outputs, the action output with name ending in .log (as opposed to .xml) is probably the
 * easiest to process for most use cases.</li>
 * <li>The BEPFileUri class that contains the action output file uri has helper functions to read in the lines of the
 * log file.</li>
 * </ul>
 */
public class BEPTestResultEvent extends BEPEvent {
    public static final String NAME = "testResult";

    private String testLabel;
    private int testRun;
    private int testShard;
    private int testAttempt;
    private final Map<String, BEPFileUri> actionOutputs = new HashMap<>();
    private int testDurationMs = 0;
    private String testStatus;
    private long testAttemptStartMillisEpoch;
    private String testStrategy;

    public BEPTestResultEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        var idDetail = (JSONObject) eventObj.get("id");
        if (idDetail != null) {
            parseId(idDetail);
        }

        var testDetail = (JSONObject) eventObj.get("testResult");
        if (testDetail != null) {
            parseDetails(testDetail);
        }
    }

    // GETTERS

    /**
     * References to the files on disk that contain the output from the test run.
     */
    public Map<String, BEPFileUri> getActionOutputs() {
        return actionOutputs;
    }

    public int getTestAttempt() {
        return testAttempt;
    }

    public long getTestAttemptStartMillisEpoch() {
        return testAttemptStartMillisEpoch;
    }

    /**
     * Duration of the test run.
     */
    public int getTestDurationMs() {
        return testDurationMs;
    }

    /**
     * The Bazel label of the failed target.
     */
    public String getTestLabel() {
        return testLabel;
    }

    public int getTestRun() {
        return testRun;
    }

    public int getTestShard() {
        return testShard;
    }

    /**
     * Result of the test, appears to be either PASSED or FAILED.
     */
    public String getTestStatus() {
        return testStatus;
    }

    /**
     * May provide values such as "darwin-sandbox", but it does not appear to be provided in many cases so don't rely on
     * this being present.
     */
    public String getTestStrategy() {
        return testStrategy;
    }

    // PARSER

    /*
      "testResult": {
       "testActionOutput": [
         {
           "name": "test.log",
           "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log"
         },
         {
           "name": "test.xml",
           "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.xml"
         }
       ],
       "testAttemptDurationMillis": "826",
       "status": "FAILED",
       "testAttemptStartMillisEpoch": "1622353495424",
       "executionInfo": { "strategy": "darwin-sandbox" }
     }
     */
    void parseDetails(JSONObject testDetail) {
        var actionOutputArray = (JSONArray) testDetail.get("testActionOutput");
        for (Object actionOutput : actionOutputArray) {
            var fileUri = decodeURIFromJsonObject(actionOutput);
            if (fileUri != null) {
                actionOutputs.put(fileUri.getId(), fileUri);
            }
        }
        testDurationMs = decodeIntFromJsonObject(testDetail.get("testAttemptDurationMillis"));
        testStatus = decodeStringFromJsonObject(testDetail.get("status"));
        if ("FAILED".equals(testStatus)) {
            isError = true;
        }
        testAttemptStartMillisEpoch = decodeLongFromJsonObject(testDetail.get("testAttemptStartMillisEpoch"));
        var execDetail = (JSONObject) testDetail.get("executionInfo");
        if (execDetail != null) {
            testStrategy = decodeStringFromJsonObject(execDetail.get("strategy"));
        }
    }

    /*
      "id": {
       "testResult": {
         "label": "//foo:foo-test",
         "run": 1,
         "shard": 1,
         "attempt": 1,
         "configuration": { "id": "63cc040ed2b86a512099924e698df6e0b9848625e6ca33d9556c5993dccbc2fb" }
       }
     },
     */
    void parseId(JSONObject idDetail) {
        var testId = (JSONObject) idDetail.get("testResult");
        if (testId != null) {
            testLabel = decodeStringFromJsonObject(testId.get("label"));
            testRun = decodeIntFromJsonObject(testId.get("run"));
            testShard = decodeIntFromJsonObject(testId.get("shard"));
            testAttempt = decodeIntFromJsonObject(testId.get("attempt"));
        }
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPTestResultEvent [testLabel=" + testLabel + ", testRun=" + testRun + ", testShard=" + testShard
                + ", testAttempt=" + testAttempt + ", testDurationMs=" + testDurationMs + ", testStatus=" + testStatus
                + ", testAttemptStartMillisEpoch=" + testAttemptStartMillisEpoch + ", testStrategy=" + testStrategy
                + ", index=" + index + ", eventType=" + eventType + ", isProcessed=" + isProcessed + ", isLastMessage="
                + isLastMessage + ", isError=" + isError + ", actionOutputs=" + actionOutputs + "]";
    }
}
