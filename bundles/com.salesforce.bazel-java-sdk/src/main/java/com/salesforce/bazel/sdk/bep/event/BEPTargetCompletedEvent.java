package com.salesforce.bazel.sdk.bep.event;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Model for the Target Completed BEP event.
 */
public class BEPTargetCompletedEvent extends BEPEvent {

    public static final String NAME = "targetCompleted";

    private String failureMessage;
    private String failureSpawnCode;
    private int failureSpawnExitCode;
    private boolean success = false;
    private List<BEPFileUri> importantOutput = new ArrayList<>();

    public BEPTargetCompletedEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject completedDetail = (JSONObject) eventObj.get("completed");
        if (completedDetail != null) {
            parseDetails(completedDetail);
        }
    }

    // GETTERS

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getFailureSpawnCode() {
        return failureSpawnCode;
    }

    public int getFailureSpawnExitCode() {
        return failureSpawnExitCode;
    }

    public boolean isSuccess() {
        return success;
    }

    /**
     * For a successful build, important outputs will be listed, which include the built artifacts from this target. The
     * name "importantOutput" comes from the BEP json format.
     */
    public List<BEPFileUri> getImportantOutput() {
        return importantOutput;
    }

    // PARSER

    /*
     FAILURE:
       "completed": {
        "failureDetail": {
          "message": "worker spawn failed for Javac",
          "spawn": {
            "code": "NON_ZERO_EXIT",
            "spawnExitCode": 1
          }
        }
      }
      
      
     SUCCESS:
       "completed": {
           "success": true,
           "importantOutput": [
            {
                "name": "foo/foo.jar",
                "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo.jar",
                "pathPrefix": [
                  "bazel-out", "darwin-fastbuild", "bin"
                ]
            },
            {
                "name": "foo/foo",
                "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo",
                "pathPrefix": [
                  "bazel-out", "darwin-fastbuild", "bin"
                ]
            }
           ]
      } 
     */

    void parseDetails(JSONObject completedDetail) {

        // FAILURE
        JSONObject failureDetailObj = (JSONObject) completedDetail.get("failureDetail");
        if (failureDetailObj != null) {
            this.isError = true;
            failureMessage = this.decodeStringFromJsonObject(failureDetailObj.get("message"));
            JSONObject spawnObj = (JSONObject) failureDetailObj.get("spawn");
            if (spawnObj != null) {
                failureSpawnCode = this.decodeStringFromJsonObject(failureDetailObj.get("code"));
                failureSpawnExitCode = this.decodeIntFromJsonObject(spawnObj.get("spawnExitCode"));
            }
        }

        // SUCCESS
        success = this.decodeBooleanFromJsonObject(completedDetail.get("success"));
        JSONArray importantOutputArray = (JSONArray) completedDetail.get("importantOutput");
        if (importantOutputArray != null && importantOutputArray.size() > 0) {
            for (int i = 0; i < importantOutputArray.size(); i++) {
                BEPFileUri fileUri = this.decodeURIFromJsonObject(importantOutputArray.get(i));
                if (fileUri != null) {
                    importantOutput.add(fileUri);
                }
            }
        }
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPTargetCompletedEvent [failureMessage=" + failureMessage + ", failureSpawnCode=" + failureSpawnCode
                + ", failureSpawnExitCode=" + failureSpawnExitCode + ", success=" + success + ", importantOutput="
                + importantOutput + ", index=" + index + ", eventType=" + eventType + ", isProcessed=" + isProcessed
                + ", isLastMessage=" + isLastMessage + ", isError=" + isError + "]";
    }

}
