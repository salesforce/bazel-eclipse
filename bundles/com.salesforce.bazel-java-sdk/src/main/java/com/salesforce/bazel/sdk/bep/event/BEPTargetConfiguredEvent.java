package com.salesforce.bazel.sdk.bep.event;

import java.util.List;

import org.json.simple.JSONObject;

/**
 * Model for the Target Configured BEP event.
 */
public class BEPTargetConfiguredEvent extends BEPEvent {

    public static final String NAME = "targetConfigured";

    private String targetLabel;
    private String targetKind;
    private String testSize;
    private List<String> tags;

    public BEPTargetConfiguredEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject idDetail = (JSONObject) eventObj.get("id");
        if (idDetail != null) {
            parseId(idDetail);
        }

        JSONObject completedDetail = (JSONObject) eventObj.get("configured");
        if (completedDetail != null) {
            parseDetails(completedDetail);
        }
    }

    // GETTERS

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getTargetKind() {
        return targetKind;
    }

    public String getTestSize() {
        return testSize;
    }

    public List<String> getTags() {
        return tags;
    }

    // PARSER

    void parseId(JSONObject idDetail) {
        JSONObject targetConfiguredObj = (JSONObject) idDetail.get("targetConfigured");
        if (targetConfiguredObj != null) {
            targetLabel = decodeStringFromJsonObject(targetConfiguredObj.get("label"));
        }
    }

    /*
    "configured": {
        "targetKind": "java_library rule",
        "testSize": "MEDIUM",
        "tag": []
      }      
     */

    void parseDetails(JSONObject completedDetail) {

        targetKind = decodeStringFromJsonObject(completedDetail.get("targetKind"));
        testSize = decodeStringFromJsonObject(completedDetail.get("testSize"));
        tags = decodeStringArrayFromJsonObject(completedDetail.get("tag"));
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPTargetConfiguredEvent [targetKind=" + targetKind + ", testSize=" + testSize + ", tags=" + tags
                + ", index=" + index + ", eventType=" + eventType + ", isProcessed=" + isProcessed + ", isLastMessage="
                + isLastMessage + ", isError=" + isError + "]";
    }
}
