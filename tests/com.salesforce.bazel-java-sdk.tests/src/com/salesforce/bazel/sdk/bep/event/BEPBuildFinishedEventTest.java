package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPBuildFinishedEventTest {

    private static final String successEvent = "{\n" + "  \"id\": { \"buildFinished\": {} },\n" + "  \"children\": [\n"
            + "    { \"buildToolLogs\": {} },\n" + "    { \"buildMetrics\": {} }\n" + "  ],\n" + "  \"finished\": {\n"
            + "    \"overallSuccess\": true,\n" + "    \"finishTimeMillis\": \"1622351858397\",\n"
            + "    \"exitCode\": { \"name\": \"SUCCESS\" },\n" + "    \"anomalyReport\": {}\n" + "  }\n" + "}";

    private static final String failEvent = "{\n" + "  \"id\": {\n" + "    \"buildFinished\": {}\n" + "  },\n"
            + "  \"children\": [\n" + "    { \"buildToolLogs\": {} },\n" + "    { \"buildMetrics\": {} }\n" + "  ],\n"
            + "  \"finished\": {\n" + "    \"finishTimeMillis\": \"1622353275709\",\n" + "    \"exitCode\": {\n"
            + "      \"name\": \"BUILD_FAILURE\",\n" + "      \"code\": 1\n" + "    },\n"
            + "    \"anomalyReport\": {}\n" + "  }\n" + "}";

    @Test
    public void testSuccessEventParse() throws Exception {
        String rawEvent = successEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPBuildFinishedEvent event = new BEPBuildFinishedEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertTrue(event.isOverallSuccess());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("buildFinished", event.getEventType());

        assertEquals("SUCCESS", event.getExitCodeName());
        assertEquals(1622351858397L, event.getFinishTimeMillis());
    }

    @Test
    public void testFailEventParse() throws Exception {
        String rawEvent = failEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPBuildFinishedEvent event = new BEPBuildFinishedEvent(rawEvent, index, jsonEvent);

        assertTrue(event.isError());
        assertFalse(event.isOverallSuccess());

        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("buildFinished", event.getEventType());

        assertEquals("BUILD_FAILURE", event.getExitCodeName());
        assertEquals(1622353275709L, event.getFinishTimeMillis());
        assertEquals(1, event.getExitCodeCode());
    }
}
