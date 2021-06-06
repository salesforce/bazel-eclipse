package com.salesforce.bazel.sdk.bep.event;

import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class BEPUnstructuredCommandLineEvent extends BEPEvent {
    public static final String NAME = "unstructuredCommandLine";

    private List<String> args;

    public BEPUnstructuredCommandLineEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject commandDetail = (JSONObject) eventObj.get("unstructuredCommandLine");
        if (commandDetail != null) {
            parseDetails(commandDetail);
        }
    }

    // GETTERS

    public List<String> getArgs() {
        return args;
    }

    // PARSER

    public void setArgs(List<String> args) {
        this.args = args;
    }

    void parseDetails(JSONObject commandDetail) {
        JSONArray argsArray = (JSONArray) commandDetail.get("args");
        args = this.decodeStringArrayFromJsonObject(argsArray);

    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPUnstructuredCommandLineEvent [args=" + args + ", index=" + index + ", eventType=" + eventType
                + ", isProcessed=" + isProcessed + ", isLastMessage=" + isLastMessage + ", isError=" + isError + "]";
    }

}
