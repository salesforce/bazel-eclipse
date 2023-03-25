package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPPatternEventTest {

    private static final String patternEvent = """
            {
              "id": {
                "pattern": {
                  "pattern": [
                    "//..."
                  ]
                }
              },
              "children": [
                { "targetConfigured": { "label": "//foo:lombok" } },
                { "targetConfigured": { "label": "//foo:foo" } },
                { "targetConfigured": { "label": "//foo:foo-test" } }
              ],
              "expanded": {}
            }""";

    @Test
    public void testPatternEventParse() throws Exception {
        var rawEvent = patternEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPPatternEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("pattern", event.getEventType());

        assertEquals("//...", event.getInputPatterns().get(0));
        assertTrue(event.getResolvedPatterns().contains("//foo:lombok"));
        assertTrue(event.getResolvedPatterns().contains("//foo:foo"));
        assertTrue(event.getResolvedPatterns().contains("//foo:foo-test"));
    }

}
