package com.salesforce.bazel.sdk.bep.event;

import org.json.simple.JSONObject;

/**
 * Model for the Build Finished BEP event.
 */
public class BEPBuildFinishedEvent extends BEPEvent {

    public static final String NAME = "buildFinished";

    private boolean overallSuccess = false;
    private long finishTimeMillis = 0L;
    private String exitCodeName;
    private int exitCodeCode;

    public BEPBuildFinishedEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject finishedDetail = (JSONObject) eventObj.get("finished");
        if (finishedDetail != null) {
            parseDetails(finishedDetail);
        }
    }

    // GETTERS

    public boolean isOverallSuccess() {
        return overallSuccess;
    }

    public long getFinishTimeMillis() {
        return finishTimeMillis;
    }

    public String getExitCodeName() {
        return exitCodeName;
    }

    public int getExitCodeCode() {
        return exitCodeCode;
    }

    // PARSER

    /*
     FAIL
      "finished": {
          "finishTimeMillis": "1622353275709",
          "exitCode": {
            "name": "BUILD_FAILURE",
            "code": 1
          },
          "anomalyReport": {}
        }
    
     SUCCESS
      "finished": {
        "overallSuccess": true,
        "finishTimeMillis": "1622351858397",
        "exitCode": { "name": "SUCCESS" },
        "anomalyReport": {}
      }
     */

    void parseDetails(JSONObject finishedDetail) {
        overallSuccess = decodeBooleanFromJsonObject(finishedDetail.get("overallSuccess"));
        this.isError = !overallSuccess;

        finishTimeMillis = this.decodeLongFromJsonObject(finishedDetail.get("finishTimeMillis"));
        JSONObject exitCodeObj = (JSONObject) finishedDetail.get("exitCode");
        if (exitCodeObj != null) {
            exitCodeName = this.decodeStringFromJsonObject(exitCodeObj.get("name"));
            exitCodeCode = this.decodeIntFromJsonObject(exitCodeObj.get("code"));
        }
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPBuildFinishedEvent [overallSuccess=" + overallSuccess + ", finishTimeMillis=" + finishTimeMillis
                + ", exitCodeName=" + exitCodeName + ", exitCodeCode=" + exitCodeCode + ", index=" + index
                + ", eventType=" + eventType + ", isProcessed=" + isProcessed + ", isLastMessage=" + isLastMessage
                + ", isError=" + isError + "]";
    }
}
