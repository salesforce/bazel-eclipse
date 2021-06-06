package com.salesforce.bazel.sdk.bep.event;

import java.util.List;

import org.json.simple.JSONObject;

/**
 * Model for the Options Parsed BEP event.
 * <p>
 * This event is useful when you want to see the arguments and parameters used by Bazel to launch the build/test
 * operation.
 */
public class BEPOptionsParsedEvent extends BEPEvent {
    public static final String NAME = "optionsParsed";

    private List<String> startupOptions;
    private List<String> explicitStartupOptions;
    private List<String> commandLine;

    public BEPOptionsParsedEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject optionsDetail = (JSONObject) eventObj.get("optionsParsed");
        if (optionsDetail != null) {
            parseDetails(optionsDetail);
        }
    }

    // GETTERS

    public List<String> getStartupOptions() {
        return startupOptions;
    }

    public List<String> getExplicitStartupOptions() {
        return explicitStartupOptions;
    }

    public List<String> getCommandLine() {
        return commandLine;
    }

    // PARSER

    /*
     "optionsParsed": {
     "startupOptions": [
       "--output_user_root=/var/tmp/_bazel_mbenioff",
       "--output_base=/private/var/tmp/_bazel_mbenioff/d9d40273485d06d9755a220abc6e68f7",
       "--host_jvm_args=-Dtest=one",
       "--host_jvm_args=-Dtest=two",
      ],
      "explicitStartupOptions": [
       "--host_jvm_args=-Dtest=one",
       "--host_jvm_args=-Dtest=two",
      ],
      "cmdLine": [
       "--javacopt=-Werror",
       "--javacopt=-Xlint:-options",
       "--javacopt=--release 11",
      ],
      "invocationPolicy": {}
     }
     */
    private void parseDetails(JSONObject optionsDetail) {
        startupOptions = this.decodeStringArrayFromJsonObject(optionsDetail.get("startupOptions"));
        explicitStartupOptions = this.decodeStringArrayFromJsonObject(optionsDetail.get("explicitStartupOptions"));
        commandLine = this.decodeStringArrayFromJsonObject(optionsDetail.get("cmdLine"));
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPOptionsParsedEvent [startupOptions=" + startupOptions + ", explicitStartupOptions="
                + explicitStartupOptions + ", commandLine=" + commandLine + ", index=" + index + ", eventType="
                + eventType + ", isProcessed=" + isProcessed + ", isLastMessage=" + isLastMessage + ", isError="
                + isError + "]";
    }
}
