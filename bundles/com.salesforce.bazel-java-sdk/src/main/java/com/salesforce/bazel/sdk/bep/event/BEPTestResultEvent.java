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
    private Map<String, BEPFileUri> actionOutputs = new HashMap<>();
    private int testDurationMs = 0;
    private String testStatus;
    private long testAttemptStartMillisEpoch;
    private String testStrategy;

    public BEPTestResultEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject idDetail = (JSONObject) eventObj.get("id");
        if (idDetail != null) {
            parseId(idDetail);
        }

        JSONObject testDetail = (JSONObject) eventObj.get("testResult");
        if (testDetail != null) {
            parseDetails(testDetail);
        }
    }

    // GETTERS

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

    public int getTestAttempt() {
        return testAttempt;
    }

    /**
     * References to the files on disk that contain the output from the test run.
     */
    public Map<String, BEPFileUri> getActionOutputs() {
        return actionOutputs;
    }

    /**
     * Duration of the test run.
     */
    public int getTestDurationMs() {
        return testDurationMs;
    }

    /**
     * Result of the test, appears to be either PASSED or FAILED.
     */
    public String getTestStatus() {
        return testStatus;
    }

    public long getTestAttemptStartMillisEpoch() {
        return testAttemptStartMillisEpoch;
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
        JSONObject testId = (JSONObject) idDetail.get("testResult");
        if (testId != null) {
            testLabel = decodeStringFromJsonObject(testId.get("label"));
            testRun = decodeIntFromJsonObject(testId.get("run"));
            testShard = decodeIntFromJsonObject(testId.get("shard"));
            testAttempt = decodeIntFromJsonObject(testId.get("attempt"));
        }
    }

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
        JSONArray actionOutputArray = (JSONArray) testDetail.get("testActionOutput");
        for (Object actionOutput : actionOutputArray) {
            BEPFileUri fileUri = this.decodeURIFromJsonObject(actionOutput);
            if (fileUri != null) {
                actionOutputs.put(fileUri.getId(), fileUri);
            }
        }
        testDurationMs = this.decodeIntFromJsonObject(testDetail.get("testAttemptDurationMillis"));
        testStatus = this.decodeStringFromJsonObject(testDetail.get("status"));
        if ("FAILED".equals(testStatus)) {
            this.isError = true;
        }
        testAttemptStartMillisEpoch = this.decodeLongFromJsonObject(testDetail.get("testAttemptStartMillisEpoch"));
        JSONObject execDetail = (JSONObject) testDetail.get("executionInfo");
        if (execDetail != null) {
            testStrategy = this.decodeStringFromJsonObject(execDetail.get("strategy"));
        }
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPTestResultEvent [testLabel=" + testLabel + ", testRun=" + testRun + ", testShard=" + testShard
                + ", testAttempt=" + testAttempt + ", actionOutputs=" + actionOutputs + ", testDurationMs="
                + testDurationMs + ", testStatus=" + testStatus + ", testAttemptStartMillisEpoch="
                + testAttemptStartMillisEpoch + ", testStrategy=" + testStrategy + ", index=" + index + ", eventType="
                + eventType + ", isProcessed=" + isProcessed + ", isLastMessage=" + isLastMessage + ", isError="
                + isError + "]";
    }
}
