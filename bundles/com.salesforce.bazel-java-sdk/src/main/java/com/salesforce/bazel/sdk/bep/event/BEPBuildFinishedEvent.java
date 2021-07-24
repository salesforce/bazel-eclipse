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
         isError = !overallSuccess;

         finishTimeMillis = decodeLongFromJsonObject(finishedDetail.get("finishTimeMillis"));
         JSONObject exitCodeObj = (JSONObject) finishedDetail.get("exitCode");
         if (exitCodeObj != null) {
             exitCodeName = decodeStringFromJsonObject(exitCodeObj.get("name"));
             exitCodeCode = decodeIntFromJsonObject(exitCodeObj.get("code"));
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
