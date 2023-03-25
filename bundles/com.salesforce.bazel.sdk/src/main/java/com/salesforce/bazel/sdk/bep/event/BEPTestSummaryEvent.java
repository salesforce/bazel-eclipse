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

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Model for the Test Summary BEP event.
 */
public class BEPTestSummaryEvent extends BEPEvent {
    public static final String NAME = "testSummary";

    private String testLabel;
    private String testStatus;
    private final List<BEPFileUri> testLogs = new ArrayList<>();
    private long firstStartTimeMillis;
    private long lastStopTimeMillis;
    private int totalRunDurationMillis;
    private int totalRunCount;
    private int runCount;

    public BEPTestSummaryEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        var idDetail = (JSONObject) eventObj.get("id");
        if (idDetail != null) {
            parseId(idDetail);
        }

        var testDetail = (JSONObject) eventObj.get("testSummary");
        if (testDetail != null) {
            parseDetails(testDetail);
        }
    }

    // GETTERS

    public long getFirstStartTimeMillis() {
        return firstStartTimeMillis;
    }

    public long getLastStopTimeMillis() {
        return lastStopTimeMillis;
    }

    public int getRunCount() {
        return runCount;
    }

    public String getTestLabel() {
        return testLabel;
    }

    public List<BEPFileUri> getTestLogs() {
        return testLogs;
    }

    public String getTestStatus() {
        return testStatus;
    }

    public int getTotalRunCount() {
        return totalRunCount;
    }

    public int getTotalRunDurationMillis() {
        return totalRunDurationMillis;
    }

    // PARSER

    /*
    "id": {
     "testSummary": {
       "label": "//foo:foo-test",
       "configuration": { "id": "63cc040ed2b86a512099924e698df6e0b9848625e6ca33d9556c5993dccbc2fb" }
     }
    }
     */

    void parseDetails(JSONObject testDetail) {
        var testLogArray = (JSONArray) testDetail.get("passed");
        if (testLogArray != null) {
            for (Object testLog : testLogArray) {
                var fileUri = decodeURIFromJsonObject(testLog);
                if (fileUri != null) {
                    testLogs.add(fileUri);
                }
            }
        }
        firstStartTimeMillis = decodeLongFromJsonObject(testDetail.get("firstStartTimeMillis"));
        lastStopTimeMillis = decodeLongFromJsonObject(testDetail.get("lastStopTimeMillis"));
        totalRunDurationMillis = decodeIntFromJsonObject(testDetail.get("totalRunDurationMillis"));

        testStatus = decodeStringFromJsonObject(testDetail.get("overallStatus"));
        if ("FAILED".equals(testStatus)) {
            isError = true;
        }

        runCount = decodeIntFromJsonObject(testDetail.get("runCount"));
        totalRunCount = decodeIntFromJsonObject(testDetail.get("totalRunCount"));
    }

    /*
      "testSummary": {
        "totalRunCount": 1,
        "passed": [
          {
           "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log"
          }
         ],
        "overallStatus": "PASSED",
        "firstStartTimeMillis": "1622354133812",
        "lastStopTimeMillis": "1622354134270",
        "totalRunDurationMillis": "458",
        "runCount": 1
      }
     */

    void parseId(JSONObject idDetail) {
        var testId = (JSONObject) idDetail.get("testSummary");
        if (testId != null) {
            testLabel = decodeStringFromJsonObject(testId.get("label"));
        }
    }

    @Override
    public String toString() {
        return "BEPTestSummaryEvent [testLabel=" + testLabel + ", testStatus=" + testStatus + ", actionOutputs="
                + testLogs + ", firstStartTimeMillis=" + firstStartTimeMillis + ", lastStopTimeMillis="
                + lastStopTimeMillis + ", totalRunDurationMillis=" + totalRunDurationMillis + ", totalRunCount="
                + totalRunCount + ", runCount=" + runCount + ", index=" + index + ", eventType=" + eventType
                + ", isProcessed=" + isProcessed + ", isLastMessage=" + isLastMessage + ", isError=" + isError + "]";
    }
}
