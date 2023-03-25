package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPBuildMetricsEventTest {

    private static final String metricsEvent = """
            {
              "id": {
                "buildMetrics": {}
              },
              "buildMetrics": {
                "actionSummary": { "actionsCreated": "2", "actionsExecuted": "5" },
                "memoryMetrics": { "usedHeapSizePostBuild":"31446304" },
                "packageMetrics": {},
                "timingMetrics": {
                  "cpuTimeInMs": "647",
                  "wallTimeInMs": "3459",
                  "analysisPhaseTimeInMs": "23",
                }
              }
            }""";

    @Test
    public void testMetricsEventParse() throws Exception {
        var rawEvent = metricsEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPBuildMetricsEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("buildMetrics", event.getEventType());

        assertEquals(2, event.getActionsCreated());
        assertEquals(5, event.getActionsExecuted());
        assertEquals(31446304L, event.getUsedHeapSizePostBuild());
        assertEquals(647, event.getCpuTimeInMs());
        assertEquals(3459, event.getWallTimeInMs());
        assertEquals(23, event.getAnalysisPhaseTimeInMs());
    }

}
