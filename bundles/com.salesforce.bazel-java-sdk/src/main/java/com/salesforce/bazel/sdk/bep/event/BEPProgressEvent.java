package com.salesforce.bazel.sdk.bep.event;

import java.util.List;

import org.json.simple.JSONObject;

/**
 * Model for the Build Progress BEP event.
 */
public class BEPProgressEvent extends BEPEvent {

    public static final String NAME = "progress";
    private static boolean includeStdOutErrInToString = true;

    protected List<String> stdout;
    protected List<String> stderr;

    public BEPProgressEvent(String rawEvent, int index, JSONObject eventObj) {
        super(NAME, rawEvent, index, eventObj);

        JSONObject progressDetail = (JSONObject) eventObj.get("progress");
        if (progressDetail != null) {
            parseDetails(progressDetail);
        }
    }

    // GETTERS

    public List<String> getStdout() {
        return stdout;
    }

    public List<String> getStderr() {
        return stderr;
    }

    // FEATURE TOGGLES

    /**
     * Calling toString() on a progress event can be very verbose if the default behavior of including stdout and stderr
     * in the output. You may enable/disable this behavior.
     */
    public static void includeStdOutErrInToString(boolean include) {
        includeStdOutErrInToString = include;
    }

    // PARSER

    void parseDetails(JSONObject progressDetail) {

        // TODO defer the heavy work of cleaning and deduping lines
        // TODO should we be doing the error detection at all, and can we defer it if we need to do a text scan

        Object stderrObj = progressDetail.get("stderr");
        if (stderrObj != null) {
            String stderrStr = stderrObj.toString();
            if (stderrStr.startsWith("ERROR:") || stderrStr.contains("FAILED")) {
                isError = true;
            }
            stderr = splitAndCleanAndDedupeLines(stderrStr);
        }
        Object stdoutObj = progressDetail.get("stdout");
        if (stdoutObj != null) {
            String stdoutStr = stdoutObj.toString();
            stdout = splitAndCleanAndDedupeLines(stdoutStr);
        }
    }

    // TOSTRING

    @Override
    public String toString() {
        String stdoutStr = includeStdOutErrInToString ? "stdout=" + stdout.toString() : "";
        String stderrStr = includeStdOutErrInToString ? ", stderr=" + stderr.toString() + ", " : "";
        return "BEPProgressEvent [" + stdoutStr + stderrStr + "index=" + index + ", eventType=" + eventType
                + ", isProcessed=" + isProcessed + ", isLastMessage=" + isLastMessage + ", isError=" + isError + "]";
    }
}
