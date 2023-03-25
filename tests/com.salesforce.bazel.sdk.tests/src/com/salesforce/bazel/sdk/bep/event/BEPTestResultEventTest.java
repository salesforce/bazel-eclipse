package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPTestResultEventTest {

    private static final String resultEvent =
            """
                    {
                      "id": {
                        "testResult": {
                          "label": "//foo:foo-test",
                          "run": 1,
                          "shard": 1,
                          "attempt": 1,
                          "configuration": { "id": "63cc040ed2b86a512099924e698df6e0b9848625e6ca33d9556c5993dccbc2fb" }
                        }
                      },
                      "testResult": {
                        "testActionOutput": [
                          {
                            "name": "test.log",
                            "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log"
                          },
                          {
                            "name": "test.xml",
                            "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.xml"
                          }
                        ],
                        "testAttemptDurationMillis": "826",
                        "status": "FAILED",
                        "testAttemptStartMillisEpoch": "1622353495424",
                        "executionInfo": { "strategy": "darwin-sandbox" }
                      }
                    }""";

    @Test
    public void testResultEventParse() throws Exception {
        var rawEvent = resultEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPTestResultEvent(rawEvent, index, jsonEvent);

        assertTrue(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("testResult", event.getEventType());

        var uri = event.getActionOutputs().get("test.log");
        assertEquals("test.log", uri.getId());
        assertEquals(
            "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log",
            uri.getUri().toString());
    }

}
