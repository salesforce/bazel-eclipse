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
         startupOptions = decodeStringArrayFromJsonObject(optionsDetail.get("startupOptions"));
         explicitStartupOptions = decodeStringArrayFromJsonObject(optionsDetail.get("explicitStartupOptions"));
         commandLine = decodeStringArrayFromJsonObject(optionsDetail.get("cmdLine"));
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
