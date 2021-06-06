package com.salesforce.bazel.sdk.bep.event;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Model for the Target Completed BEP event.
 * <p>
 * This event is useful when you want to see the resolution of wildcard build targets, such as //foo/bar/... This event
 * lists the concrete targets that are resolved from the wildcard.
 */
public class BEPPatternEvent extends BEPEvent {
    public static final String NAME = "pattern";

    private List<String> inputPatterns = new ArrayList<>();
    private List<String> resolvedPatterns = new ArrayList<>();

    public BEPPatternEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject idDetail = (JSONObject) eventObj.get("id");
        if (idDetail != null) {
            parseInputPatterns(idDetail);
        }
        JSONArray childrenArray = (JSONArray) eventObj.get("children");
        if (childrenArray != null) {
            parseResolvedPatterns(childrenArray);
        }
    }

    // GETTERS

    public List<String> getInputPatterns() {
        return inputPatterns;
    }

    public List<String> getResolvedPatterns() {
        return resolvedPatterns;
    }

    // PARSING

    /*
     "id": {
      "pattern": {
        "pattern": [
          "//..."
        ]
      }
     },
     */

    private void parseInputPatterns(JSONObject idDetail) {
        JSONObject patternDetail = (JSONObject) idDetail.get("pattern");
        if (patternDetail != null) {
            // "Hey Google, why are there two levels of 'pattern'?" 
            JSONArray patternArray = (JSONArray) patternDetail.get("pattern");
            if (patternArray != null && patternArray.size() > 0) {
                for (int i = 0; i < patternArray.size(); i++) {
                    inputPatterns.add(patternArray.get(i).toString());
                }
            }
        }
    }

    /*
     "children": [
       { "targetConfigured": { "label": "//foo:lombok" } },
       { "targetConfigured": { "label": "//foo:foo" } },
       { "targetConfigured": { "label": "//foo:foo-test" } }
     ],
     */
    private void parseResolvedPatterns(JSONArray childrenArray) {
        for (int i = 0; i < childrenArray.size(); i++) {
            JSONObject childObj = (JSONObject) childrenArray.get(i);
            if (childObj != null) {
                JSONObject targetConfiguredObj = (JSONObject) childObj.get("targetConfigured");
                if (targetConfiguredObj != null) {
                    String labelStr = this.decodeStringFromJsonObject(targetConfiguredObj.get("label"));
                    if (labelStr != null) {
                        resolvedPatterns.add(labelStr);
                    }
                }
            }
        }
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPPatternEvent [inputPatterns=" + inputPatterns + ", resolvedPatterns=" + resolvedPatterns + ", index="
                + index + ", eventType=" + eventType + ", isProcessed=" + isProcessed + ", isLastMessage="
                + isLastMessage + ", isError=" + isError + "]";
    }

}
