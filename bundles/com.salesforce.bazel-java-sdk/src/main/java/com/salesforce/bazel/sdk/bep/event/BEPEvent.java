package com.salesforce.bazel.sdk.bep.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Base model of a Build Event Protocol (BEP) event, and parsing utilities. Check for subclasses for specific event type
 * implementations.
 * <p>
 * BEP is strangely difficult to parse. The contents of the json attributes are denormalized and not fit for computer
 * consumption.
 * <p>
 * <a href="https://docs.bazel.build/versions/master/build-event-protocol.html">BEP Documentation</a>
 */
public class BEPEvent {
    private static final LogHelper LOG = LogHelper.log(BEPEvent.class);

    // Keeping the raw JSON string for each event can be helpful during debugging, but takes a lot
    // of memory, so this disabled by default
    private static boolean keepRawJsonString = false;

    // Keeping the json object for each event is useful if the SDK does not model a feature of
    // the event that you need, but this takes a lot of memory, so this disabled by default
    private static boolean keepJsonObject = false;

    protected int index = 0;
    protected String eventType;
    protected String rawEventString;
    protected JSONObject eventObject;

    protected boolean isProcessed = false;

    protected boolean isLastMessage = false;
    protected boolean isError = false;

    public BEPEvent(String eventType, String rawEvent, int index, JSONObject eventObj) {
        this.eventType = eventType;
        this.index = index;

        if (keepRawJsonString) {
            rawEventString = rawEvent;
        }
        if (keepJsonObject) {
            eventObject = eventObj;
        }

        // eventObj is null for event types we ignore (see BazelBuildEventTypeManager.parseEvent())
        if (eventObj != null) {
            // any event (theoretically) could be the lastMessage, so check that here in the base event
            Object lastMessage = eventObj.get("lastMessage");
            if (lastMessage != null) {
                isLastMessage = true;
            }
        }
    }

    // GETTERS

    /**
     * Returns the event type (e.g. progress, started, testResult)
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * The numerical index of this event in the BEP file. Starts at 0.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the JSON String, intended to be used for debugging. Since this can take a lot of memory, you must enable
     * this feature explicitly:<br/>
     * Call this static method: <b>keepEventJsonString()</b>
     */
    public String getRawEventString() {
        return rawEventString;
    }

    /**
     * Returns the JSON object, for cases in which the SDK does not model the event data that you need. Since this can
     * take a lot of memory, you must enable this feature explicitly:<br/>
     * Call this static method: <b>keepEventJsonObject()</b>
     */
    public JSONObject getJsonEventObject() {
        return eventObject;
    }

    // STATUS METHODS

    /**
     * Is this the last message in the stream in the BEP file? If true, indicates that the operation (build, test, etc)
     * is complete.
     */
    public boolean isLastMessage() {
        return isLastMessage;
    }

    /**
     * Does this event signal a failure of the entire operation (build error, test failure)?
     */
    public boolean isError() {
        return isError;
    }

    /**
     * Has this event been delivered to any interested subscriber.
     */
    public boolean isProcessed() {
        return isProcessed;
    }

    // FEATURE TOGGLES

    /**
     * Keeping the raw JSON string for each event can be helpful during debugging, but takes a lot of memory, so this
     * disabled by default.
     * <p>
     * Passing true to this method will enable the method getRawEventString()
     */
    public static void keepEventJsonString(boolean keep) {
        keepRawJsonString = keep;
    }

    /**
     * Keeping the JSON object for each event is useful if the SDK does not model a feature of the event that you need,
     * but this takes a lot of memory, so this disabled by default.
     * <p>
     * Passing true to this method will enable the method getJsonEventObject();
     */
    public static void keepEventJsonObject(boolean keep) {
        keepJsonObject = keep;
    }

    // INTERNALS

    /**
     * This event has been delivered to any interested subscriber.
     */
    public void processed() {
        isProcessed = true;
    }

    // JSON HELPERS

    /**
     * The contents of a BEP progress event is crowded with noisy characters and duplicated text. This method cleans it
     * up to an extent, but it will always be up to the caller of the SDK to fully clean it depending on purpose.
     * <p>
     * This is in the base class, not the Progress subclass, just in case other event types need to do the same
     * processing.
     */
    protected static List<String> splitAndCleanAndDedupeLines(String rawString) {
        Set<Integer> hashcodes = new HashSet<>();
        List<String> lines = new ArrayList<>();
        String[] rawLines = rawString.split("\r");
        for (String line : rawLines) {
            line = stripControlCharacters(line);
            line = line.trim();
            if (line.trim().isEmpty()) {
                // ignore blank lines
                continue;
            }

            int hashcode = line.hashCode();
            if (hashcodes.contains(hashcode)) {
                // dupe line, ignore
                continue;
            }
            hashcodes.add(hashcode);
            lines.add(line);
        }
        return lines;
    }

    /**
     * Yep, there are strange characters (ncurses?) in the stderr for BEP progress events.
     * <p>
     * This is in the base class, not the Progress subclass, just in case other event types need to do the same
     * processing.
     */
    protected static String stripControlCharacters(String rawString) {
        // first, get rid of the unicode control characters
        String clean = rawString.replaceAll("[^\\x00-\\x7F]", "");

        // next, get rid of the ASCII control characters
        clean = clean.replace("[0m", "");
        clean = clean.replace("[1m", "");
        clean = clean.replace("[32m", "");
        clean = clean.replace("[31m", "");

        // and then these
        clean = clean.replace("[1A", "");
        clean = clean.replace("[K", "");

        // in test results, after the first INFO: Build completed, 1 test FAILED, 3 total actions line
        // there is a lot of duplicated text
        int totalActionsIndex = clean.indexOf("total actions");
        if (totalActionsIndex > 0) {
            clean = clean.substring(0, totalActionsIndex + 13);
        }

        return clean;
    }

    protected String decodeStringFromJsonObject(Object valueObj) {
        String value = null;
        if (valueObj != null) {
            value = valueObj.toString();
        }
        return value;
    }

    protected boolean decodeBooleanFromJsonObject(Object valueObj) {
        boolean value = false;
        if (valueObj != null) {
            value = "true".equals(valueObj.toString());
        }
        return value;
    }

    protected int decodeIntFromJsonObject(Object valueObj) {
        int value = 0;
        if (valueObj != null) {
            try {
                value = Integer.parseInt(valueObj.toString());
            } catch (NumberFormatException nfe) {
                LOG.error("error decoding integer from json field [{}]", nfe, valueObj);
            }
        }
        return value;
    }

    protected long decodeLongFromJsonObject(Object valueObj) {
        long value = 0;
        if (valueObj != null) {
            try {
                value = Long.parseLong(valueObj.toString());
            } catch (NumberFormatException nfe) {
                LOG.error("error decoding long from json field [{}]", nfe, valueObj);
            }
        }
        return value;
    }

    protected List<String> decodeStringArrayFromJsonObject(Object array) {
        List<String> values = new ArrayList<>();

        if (array != null) {
            JSONArray jsonArray = (JSONArray) array;
            for (int i = 0; i < jsonArray.size(); i++) {
                values.add(jsonArray.get(i).toString());
            }
        }

        return values;
    }

    /**
     * Parses json properties of the following pattern. The pathPrefix property is optional. { "name": "foo/foo.jar",
     * "uri": "file:///private/foo/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo.jar", "pathPrefix": [ "bazel-out",
     * "darwin-fastbuild", "bin" ] }
     */
    protected BEPFileUri decodeURIFromJsonObject(Object valueObj) {
        BEPFileUri fileUri = null;
        JSONObject jsonObj = (JSONObject) valueObj;
        String name = decodeStringFromJsonObject(jsonObj.get("name"));
        String uri = decodeStringFromJsonObject(jsonObj.get("uri"));
        List<String> prefixes = null;

        JSONArray prefixArray = (JSONArray) jsonObj.get("pathPrefix");
        if ((prefixArray != null) && (prefixArray.size() > 0)) {
            prefixes = new ArrayList<>();
            for (int i = 0; i < prefixArray.size(); i++) {
                prefixes.add(prefixArray.get(i).toString());
            }
        }
        LOG.debug("decoded URI: {}", uri);
        if (uri != null) {
            fileUri = new BEPFileUri(name, uri, prefixes);
        }
        return fileUri;
    }

    // TOSTRING

    @Override
    public String toString() {
        return "BEPEvent [index=" + index + ", eventType=" + eventType + ", isProcessed=" + isProcessed
                + ", isLastMessage=" + isLastMessage + ", isError=" + isError + "]";
    }
}
