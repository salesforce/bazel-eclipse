package com.salesforce.bazel.sdk.bep.event;

import org.json.simple.JSONObject;

/**
 * Model for the Build Started BEP event.
 */
public class BEPStartedEvent extends BEPEvent {
    public static final String NAME = "started";

    // Details
    private String uuid;
    private long startTimeMillis = 0L;
    private String buildToolVersion;
    private String optionsDescription;
    private String command;
    private String workingDirectory;
    private String workspaceDirectory;
    private String serverPid;

    public BEPStartedEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject startedDetail = (JSONObject) eventObj.get("started");
        if (startedDetail != null) {
            parseDetails(startedDetail);
        }
    }

    // GETTERS

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public static String getName() {
        return NAME;
    }

    public String getUuid() {
        return uuid;
    }

    public String getBuildToolVersion() {
        return buildToolVersion;
    }

    public String getOptionsDescription() {
        return optionsDescription;
    }

    public String getCommand() {
        return command;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getWorkspaceDirectory() {
        return workspaceDirectory;
    }

    public String getServerPid() {
        return serverPid;
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
        uuid = this.decodeStringFromJsonObject(startedDetail.get("uuid"));
        startTimeMillis = this.decodeLongFromJsonObject(startedDetail.get("startTimeMillis"));
        buildToolVersion = this.decodeStringFromJsonObject(startedDetail.get("buildToolVersion"));
        optionsDescription = this.decodeStringFromJsonObject(startedDetail.get("optionsDescription"));
        command = this.decodeStringFromJsonObject(startedDetail.get("command"));
        workingDirectory = this.decodeStringFromJsonObject(startedDetail.get("workingDirectory"));
        workspaceDirectory = this.decodeStringFromJsonObject(startedDetail.get("workspaceDirectory"));
        serverPid = this.decodeStringFromJsonObject(startedDetail.get("serverPid"));
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
