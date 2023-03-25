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
 * Model for the Build Started BEP event.
 */
public class BEPStartedEvent extends BEPEvent {
    public static final String NAME = "started";

    public static String getName() {
        return NAME;
    }

    // Details
    private String uuid;
    private long startTimeMillis = 0L;
    private String buildToolVersion;
    private String optionsDescription;
    private String command;
    private String workingDirectory;
    private String workspaceDirectory;

    private String serverPid;

    // GETTERS

    public BEPStartedEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        var startedDetail = (JSONObject) eventObj.get("started");
        if (startedDetail != null) {
            parseDetails(startedDetail);
        }
    }

    public String getBuildToolVersion() {
        return buildToolVersion;
    }

    public String getCommand() {
        return command;
    }

    public String getOptionsDescription() {
        return optionsDescription;
    }

    public String getServerPid() {
        return serverPid;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public String getUuid() {
        return uuid;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getWorkspaceDirectory() {
        return workspaceDirectory;
    }

    // PARSER

    /*
    "started": {
       "uuid": "b4fa160a-2233-48de-b4d1-463a20c67256",
       "startTimeMillis": "1622343691246",
       "buildToolVersion": "3.7.1",
       "optionsDescription": "--javacopt=-Werror --javacopt=-Xlint:-options --javacopt='--release 11'",
       "command": "build",
       "workingDirectory": "/Users/mbenioff/dev/myrepo",
       "workspaceDirectory": "/Users/mbenioff/dev/myrepo",
       "serverPid": "58316"
    }
     */

    void parseDetails(JSONObject startedDetail) {
        uuid = decodeStringFromJsonObject(startedDetail.get("uuid"));
        startTimeMillis = decodeLongFromJsonObject(startedDetail.get("startTimeMillis"));
        buildToolVersion = decodeStringFromJsonObject(startedDetail.get("buildToolVersion"));
        optionsDescription = decodeStringFromJsonObject(startedDetail.get("optionsDescription"));
        command = decodeStringFromJsonObject(startedDetail.get("command"));
        workingDirectory = decodeStringFromJsonObject(startedDetail.get("workingDirectory"));
        workspaceDirectory = decodeStringFromJsonObject(startedDetail.get("workspaceDirectory"));
        serverPid = decodeStringFromJsonObject(startedDetail.get("serverPid"));
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPStartedEvent [uuid=" + uuid + ", startTimeMillis=" + startTimeMillis + ", buildToolVersion="
                + buildToolVersion + ", optionsDescription=" + optionsDescription + ", command=" + command
                + ", workingDirectory=" + workingDirectory + ", workspaceDirectory=" + workspaceDirectory
                + ", serverPid=" + serverPid + ", index=" + index + ", eventType=" + eventType + ", isProcessed="
                + isProcessed + ", isLastMessage=" + isLastMessage + ", isError=" + isError + "]";
    }

}
