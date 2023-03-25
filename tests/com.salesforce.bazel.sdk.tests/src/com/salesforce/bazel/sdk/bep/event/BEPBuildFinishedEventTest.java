package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPBuildFinishedEventTest {

    private static final String successEvent = """
            {
              "id": { "buildFinished": {} },
              "children": [
                { "buildToolLogs": {} },
                { "buildMetrics": {} }
              ],
              "finished": {
                "overallSuccess": true,
                "finishTimeMillis": "1622351858397",
                "exitCode": { "name": "SUCCESS" },
                "anomalyReport": {}
              }
            }""";

    private static final String failEvent = """
            {
              "id": {
                "buildFinished": {}
              },
              "children": [
                { "buildToolLogs": {} },
                { "buildMetrics": {} }
              ],
              "finished": {
                "finishTimeMillis": "1622353275709",
                "exitCode": {
                  "name": "BUILD_FAILURE",
                  "code": 1
                },
                "anomalyReport": {}
              }
            }""";

    @Test
    public void testFailEventParse() throws Exception {
        var rawEvent = failEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPBuildFinishedEvent(rawEvent, index, jsonEvent);

        assertTrue(event.isError());
        assertFalse(event.isOverallSuccess());

        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("buildFinished", event.getEventType());

        assertEquals("BUILD_FAILURE", event.getExitCodeName());
        assertEquals(1622353275709L, event.getFinishTimeMillis());
        assertEquals(1, event.getExitCodeCode());
    }

    @Test
    public void testSuccessEventParse() throws Exception {
        var rawEvent = successEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPBuildFinishedEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertTrue(event.isOverallSuccess());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("buildFinished", event.getEventType());

        assertEquals("SUCCESS", event.getExitCodeName());
        assertEquals(1622351858397L, event.getFinishTimeMillis());
    }
}
