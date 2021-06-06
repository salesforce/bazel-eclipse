package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPOptionsParsedEventTest {

    private static final String optionsEvent = "{\n" + "  \"id\": { \"optionsParsed\": {} },\n"
            + "  \"optionsParsed\": {\n" + "    \"startupOptions\": [\n"
            + "      \"--output_user_root=/var/tmp/_bazel_mbenioff\",\n"
            + "      \"--output_base=/private/var/tmp/_bazel_mbenioff/d9d40273485d06d9755a220abc6e68f7\",\n"
            + "      \"--host_jvm_args=-Dtest=one\",\n" + "      \"--host_jvm_args=-Dtest=two\",\n" + "    ],\n"
            + "    \"explicitStartupOptions\": [\n" + "      \"--host_jvm_args=-Dtest=three\",\n"
            + "      \"--host_jvm_args=-Dtest=four\",\n" + "    ],\n" + "    \"cmdLine\": [\n"
            + "      \"--javacopt=-Werror\",\n" + "      \"--javacopt=-Xlint:-options\",\n"
            + "      \"--javacopt=--release 11\",\n" + "    ],\n" + "    \"invocationPolicy\": {}\n" + "  }\n" + "}";

    @Test
    public void testOptionsEventParse() throws Exception {
        String rawEvent = optionsEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPOptionsParsedEvent event = new BEPOptionsParsedEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("optionsParsed", event.getEventType());

        assertTrue(event.getStartupOptions().contains("--output_user_root=/var/tmp/_bazel_mbenioff"));
        assertTrue(event.getStartupOptions().contains("--host_jvm_args=-Dtest=two"));
        assertTrue(event.getExplicitStartupOptions().contains("--host_jvm_args=-Dtest=three"));
        assertTrue(event.getExplicitStartupOptions().contains("--host_jvm_args=-Dtest=four"));
        assertTrue(event.getCommandLine().contains("--javacopt=-Werror"));
        assertTrue(event.getCommandLine().contains("--javacopt=--release 11"));
    }

}
