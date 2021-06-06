package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPStartedEventTest {

    private static final String startedEvent = "{\n" + "  \"id\": {\n" + "    \"started\": {}\n" + "  },\n"
            + "  \"children\": [\n" + "    { \"progress\": {} },\n" + "    { \"unstructuredCommandLine\": {} },\n"
            + "    { \"structuredCommandLine\": { \"commandLineLabel\": \"original\" } },\n"
            + "    { \"structuredCommandLine\": { \"commandLineLabel\": \"canonical\" } },\n"
            + "    { \"structuredCommandLine\": { \"commandLineLabel\": \"tool\" } },\n"
            + "    { \"buildMetadata\": {} },\n" + "    { \"optionsParsed\": {} },\n"
            + "    { \"workspaceStatus\": {} },\n" + "    { \"pattern\": {\n" + "        \"pattern\": [ \"//...\" ]\n"
            + "      }\n" + "    },\n" + "    { \"buildFinished\": {} }\n" + "  ],\n" + "  \"started\": {\n"
            + "    \"uuid\": \"b4fa160a-2233-48de-b4d1-463a20c67256\",\n"
            + "    \"startTimeMillis\": \"1622343691246\",\n" + "    \"buildToolVersion\": \"3.7.1\",\n"
            + "    \"optionsDescription\": \"--javacopt=-Werror --javacopt=-Xlint:-options --javacopt='--release 11'\",\n"
            + "    \"command\": \"build\",\n" + "    \"workingDirectory\": \"/Users/mbenioff/dev/myrepo\",\n"
            + "    \"workspaceDirectory\": \"/Users/mbenioff/dev/myrepo\",\n" + "    \"serverPid\": \"58316\"\n"
            + "  }\n" + "}";

    @Test
    public void testStartedEventParse() throws Exception {
        String rawEvent = startedEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPStartedEvent event = new BEPStartedEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("started", event.getEventType());

        assertEquals("b4fa160a-2233-48de-b4d1-463a20c67256", event.getUuid());
        assertEquals(1622343691246L, event.getStartTimeMillis());
        assertEquals("3.7.1", event.getBuildToolVersion());
        assertEquals("--javacopt=-Werror --javacopt=-Xlint:-options --javacopt='--release 11'",
            event.getOptionsDescription());
        assertEquals("build", event.getCommand());
        assertEquals("/Users/mbenioff/dev/myrepo", event.getWorkingDirectory());
        assertEquals("/Users/mbenioff/dev/myrepo", event.getWorkspaceDirectory());
        assertEquals("58316", event.getServerPid());
    }

}
