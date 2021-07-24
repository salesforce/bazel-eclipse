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
  * <p>
  * This event is useful when you want to see the resolution of wildcard build targets, such as //foo/bar/... This event
  * lists the concrete targets that are resolved from the wildcard.
  */
 public class BEPPatternEvent extends BEPEvent {
     public static final String NAME = "pattern";

     private final List<String> inputPatterns = new ArrayList<>();
     private final List<String> resolvedPatterns = new ArrayList<>();

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
             if ((patternArray != null) && (patternArray.size() > 0)) {
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
                     String labelStr = decodeStringFromJsonObject(targetConfiguredObj.get("label"));
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
