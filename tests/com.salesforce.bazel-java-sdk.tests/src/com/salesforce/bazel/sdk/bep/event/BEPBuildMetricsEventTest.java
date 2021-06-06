package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPBuildMetricsEventTest {

    private static final String metricsEvent = "{\n" + "  \"id\": {\n" + "    \"buildMetrics\": {}\n" + "  },\n"
            + "  \"buildMetrics\": {\n"
            + "    \"actionSummary\": { \"actionsCreated\": \"2\", \"actionsExecuted\": \"5\" },\n"
            + "    \"memoryMetrics\": { \"usedHeapSizePostBuild\":\"31446304\" },\n" + "    \"packageMetrics\": {},\n"
            + "    \"timingMetrics\": {\n" + "      \"cpuTimeInMs\": \"647\",\n" + "      \"wallTimeInMs\": \"3459\",\n"
            + "      \"analysisPhaseTimeInMs\": \"23\",\n" + "    }\n" + "  }\n" + "}";

    @Test
    public void testMetricsEventParse() throws Exception {
        String rawEvent = metricsEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPBuildMetricsEvent event = new BEPBuildMetricsEvent(rawEvent, index, jsonEvent);

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
