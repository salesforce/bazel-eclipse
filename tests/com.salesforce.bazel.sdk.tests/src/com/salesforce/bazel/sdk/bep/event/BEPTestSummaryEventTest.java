package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPTestSummaryEventTest {

    private static final String summaryEvent =
            """
                    {
                      "id": {
                        "testSummary": {
                          "label": "//foo:foo-test",
                          "configuration": {
                            "id": "63cc040ed2b86a512099924e698df6e0b9848625e6ca33d9556c5993dccbc2fb"
                          }
                        }
                      },
                      "testSummary": {
                        "totalRunCount": 1,
                        "passed": [
                          {
                            "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log"
                          }
                        ],
                        "overallStatus": "PASSED",
                        "firstStartTimeMillis": "1622354133812",
                        "lastStopTimeMillis": "1622354134270",
                        "totalRunDurationMillis": "458",
                        "runCount": 1
                      }
                    }""";

    @Test
    public void testPatternEventParse() throws Exception {
        var rawEvent = summaryEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPTestSummaryEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("testSummary", event.getEventType());

        assertEquals("//foo:foo-test", event.getTestLabel());
        assertEquals(
            "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log",
            event.getTestLogs().get(0).getUri().toString());

        assertEquals("PASSED", event.getTestStatus());
        assertEquals(1622354133812L, event.getFirstStartTimeMillis());
        assertEquals(1622354134270L, event.getLastStopTimeMillis());
        assertEquals(458, event.getTotalRunDurationMillis());
        assertEquals(1, event.getTotalRunCount());
    }

}
