package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPStartedEventTest {

    private static final String startedEvent = """
            {
              "id": {
                "started": {}
              },
              "children": [
                { "progress": {} },
                { "unstructuredCommandLine": {} },
                { "structuredCommandLine": { "commandLineLabel": "original" } },
                { "structuredCommandLine": { "commandLineLabel": "canonical" } },
                { "structuredCommandLine": { "commandLineLabel": "tool" } },
                { "buildMetadata": {} },
                { "optionsParsed": {} },
                { "workspaceStatus": {} },
                { "pattern": {
                    "pattern": [ "//..." ]
                  }
                },
                { "buildFinished": {} }
              ],
              "started": {
                "uuid": "b4fa160a-2233-48de-b4d1-463a20c67256",
                "startTimeMillis": "1622343691246",
                "buildToolVersion": "3.7.1",
                "optionsDescription": "--javacopt=-Werror --javacopt=-Xlint:-options --javacopt='--release 11'",
                "command": "build",
                "workingDirectory": "/Users/mbenioff/dev/myrepo",
                "workspaceDirectory": "/Users/mbenioff/dev/myrepo",
                "serverPid": "58316"
              }
            }""";

    @Test
    public void testStartedEventParse() throws Exception {
        var rawEvent = startedEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPStartedEvent(rawEvent, index, jsonEvent);

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
