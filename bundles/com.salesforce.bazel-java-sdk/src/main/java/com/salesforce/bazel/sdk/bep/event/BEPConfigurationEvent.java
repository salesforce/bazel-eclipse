package com.salesforce.bazel.sdk.bep.event;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

/**
 * Model for the Configuration BEP event.
 */
public class BEPConfigurationEvent extends BEPEvent {

    public static final String NAME = "configuration";
    private String mnemonic;
    private String platformName;
    private String cpu;
    private Map<String, String> makeVariables = new HashMap<>();

    public BEPConfigurationEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject configDetail = (JSONObject) eventObj.get("configuration");
        if (configDetail != null) {
            parseDetails(configDetail);
        }
    }

    // GETTERS

    public String getMnemonic() {
        return mnemonic;
    }

    public String getPlatformName() {
        return platformName;
    }

    public String getCpu() {
        return cpu;
    }

    public Map<String, String> getMakeVariables() {
        return makeVariables;
    }

    // PARSING

    /*
     "configuration": {
       "mnemonic": "darwin-fastbuild",
       "platformName": "darwin",
       "cpu": "darwin",
       "makeVariable": {
         "COMPILATION_MODE": "fastbuild",
         "TARGET_CPU": "darwin",
         "GENDIR": "bazel-out/darwin-fastbuild/bin",
         "BINDIR": "bazel-out/darwin-fastbuild/bin"
       }
     }
     */

    void parseDetails(JSONObject configDetail) {
        mnemonic = this.decodeStringFromJsonObject(configDetail.get("mnemonic"));
        platformName = this.decodeStringFromJsonObject(configDetail.get("platformName"));
        cpu = this.decodeStringFromJsonObject(configDetail.get("cpu"));

        JSONObject makeVariablesObj = (JSONObject) configDetail.get("makeVariable");
        if (makeVariablesObj != null) {
            for (Object key : makeVariablesObj.keySet()) {
                Object value = makeVariablesObj.get(key);
                if (value != null) {
                    makeVariables.put(key.toString(), value.toString());
                }
            }
        }
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPConfigurationEvent [mnemonic=" + mnemonic + ", platformName=" + platformName + ", cpu=" + cpu
                + ", makeVariables=" + makeVariables + ", index=" + index + ", eventType=" + eventType
                + ", isProcessed=" + isProcessed + ", isLastMessage=" + isLastMessage + ", isError=" + isError + "]";
    }

}
