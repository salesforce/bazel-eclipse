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
 * Model for the Target Completed BEP event.
 */
public class BEPTargetCompletedEvent extends BEPEvent {

    public static final String NAME = "targetCompleted";

    private String failureMessage;
    private String failureSpawnCode;
    private int failureSpawnExitCode;
    private boolean success = false;
    private final List<BEPFileUri> importantOutput = new ArrayList<>();

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
            isError = true;
            failureMessage = decodeStringFromJsonObject(failureDetailObj.get("message"));
            JSONObject spawnObj = (JSONObject) failureDetailObj.get("spawn");
            if (spawnObj != null) {
                failureSpawnCode = decodeStringFromJsonObject(failureDetailObj.get("code"));
                failureSpawnExitCode = decodeIntFromJsonObject(spawnObj.get("spawnExitCode"));
            }
        }

        // SUCCESS
        success = decodeBooleanFromJsonObject(completedDetail.get("success"));
        JSONArray importantOutputArray = (JSONArray) completedDetail.get("importantOutput");
        if ((importantOutputArray != null) && (importantOutputArray.size() > 0)) {
            for (int i = 0; i < importantOutputArray.size(); i++) {
                BEPFileUri fileUri = decodeURIFromJsonObject(importantOutputArray.get(i));
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
