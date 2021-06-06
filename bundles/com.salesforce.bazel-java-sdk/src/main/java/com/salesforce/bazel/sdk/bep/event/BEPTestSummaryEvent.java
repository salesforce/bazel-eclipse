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
    private List<BEPFileUri> testLogs = new ArrayList<>();
    private long firstStartTimeMillis;
    private long lastStopTimeMillis;
    private int totalRunDurationMillis;
    private int totalRunCount;
    private int runCount;

    public BEPTestSummaryEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject idDetail = (JSONObject) eventObj.get("id");
        if (idDetail != null) {
            parseId(idDetail);
        }

        JSONObject testDetail = (JSONObject) eventObj.get("testSummary");
        if (testDetail != null) {
            parseDetails(testDetail);
        }
    }

    // GETTERS

    public String getTestLabel() {
        return testLabel;
    }

    public String getTestStatus() {
        return testStatus;
    }

    public List<BEPFileUri> getTestLogs() {
        return testLogs;
    }

    public long getFirstStartTimeMillis() {
        return firstStartTimeMillis;
    }

    public long getLastStopTimeMillis() {
        return lastStopTimeMillis;
    }

    public int getTotalRunDurationMillis() {
        return totalRunDurationMillis;
    }

    public int getTotalRunCount() {
        return totalRunCount;
    }

    public int getRunCount() {
        return runCount;
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

    void parseId(JSONObject idDetail) {
        JSONObject testId = (JSONObject) idDetail.get("testSummary");
        if (testId != null) {
            testLabel = decodeStringFromJsonObject(testId.get("label"));
        }
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

    void parseDetails(JSONObject testDetail) {
        JSONArray testLogArray = (JSONArray) testDetail.get("passed");
        for (Object testLog : testLogArray) {
            BEPFileUri fileUri = this.decodeURIFromJsonObject(testLog);
            if (fileUri != null) {
                testLogs.add(fileUri);
            }
        }
        firstStartTimeMillis = this.decodeLongFromJsonObject(testDetail.get("firstStartTimeMillis"));
        lastStopTimeMillis = this.decodeLongFromJsonObject(testDetail.get("lastStopTimeMillis"));
        totalRunDurationMillis = this.decodeIntFromJsonObject(testDetail.get("totalRunDurationMillis"));

        testStatus = this.decodeStringFromJsonObject(testDetail.get("overallStatus"));
        if ("FAILED".equals(testStatus)) {
            this.isError = true;
        }

        runCount = this.decodeIntFromJsonObject(testDetail.get("runCount"));
        totalRunCount = this.decodeIntFromJsonObject(testDetail.get("totalRunCount"));
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
