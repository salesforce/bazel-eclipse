package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPOptionsParsedEventTest {

    private static final String optionsEvent = """
            {
              "id": { "optionsParsed": {} },
              "optionsParsed": {
                "startupOptions": [
                  "--output_user_root=/var/tmp/_bazel_mbenioff",
                  "--output_base=/private/var/tmp/_bazel_mbenioff/d9d40273485d06d9755a220abc6e68f7",
                  "--host_jvm_args=-Dtest=one",
                  "--host_jvm_args=-Dtest=two",
                ],
                "explicitStartupOptions": [
                  "--host_jvm_args=-Dtest=three",
                  "--host_jvm_args=-Dtest=four",
                ],
                "cmdLine": [
                  "--javacopt=-Werror",
                  "--javacopt=-Xlint:-options",
                  "--javacopt=--release 11",
                ],
                "invocationPolicy": {}
              }
            }""";

    @Test
    public void testOptionsEventParse() throws Exception {
        var rawEvent = optionsEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPOptionsParsedEvent(rawEvent, index, jsonEvent);

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
