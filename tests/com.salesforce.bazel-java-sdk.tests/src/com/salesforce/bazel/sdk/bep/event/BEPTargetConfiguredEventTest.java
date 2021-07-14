package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPTargetConfiguredEventTest {

    private static final String configuredEvent = "{\n" + "  \"id\": {\n" + "    \"targetConfigured\": {\n"
            + "      \"label\": \"//projects/foo/abstractions:abstractions\"\n" + "    }\n" + "  },\n"
            + "  \"children\": [\n" + "    {\n" + "      \"targetCompleted\": {\n"
            + "        \"label\": \"//projects/foo/abstractions:abstractions\",\n"
            + "        \"configuration\": {\n"
            + "          \"id\": \"1aee508e1d8c40d63ce4bd544a171e81ac2463f0e7d2f7a8dd4d4ddf19a5366e\"\n" + "        }\n"
            + "      }\n" + "    }\n" + "  ],\n" + "  \"configured\": {\n"
            + "    \"targetKind\": \"java_library rule\"\n" + "  }\n" + "}";

    @Test
    public void testConfiguredEventParse() throws Exception {
        String rawEvent = configuredEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPTargetConfiguredEvent event = new BEPTargetConfiguredEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("targetConfigured", event.getEventType());

        assertEquals("java_library rule", event.getTargetKind());
    }

}
