package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPTargetCompletedEventTest {

    private static final String successEvent =
            """
                    {
                      "id": {
                        "targetCompleted": {
                          "label": "//foo:foo",
                          "configuration": { "id": "63cc040ed2b86a512099924e698df6e0b9848625e6ca33d9556c5993dccbc2fb" }
                        }
                      },
                      "completed": {
                        "success": true,
                        "outputGroup": [
                          {
                            "name": "default",
                            "fileSets": [ { "id": "2" } ]
                          }
                        ],
                        "importantOutput": [
                          {
                            "name": "foo/foo.jar",
                            "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo.jar",
                            "pathPrefix": [
                              "bazel-out", "darwin-fastbuild", "bin"
                            ]
                          },
                          {
                            "name": "foo/foo",
                            "uri": "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo",
                            "pathPrefix": [
                              "bazel-out", "darwin-fastbuild", "bin"
                            ]
                          }
                        ]
                      }
                    }""";

    @Test
    public void testSuccessEventParse() throws Exception {
        var rawEvent = successEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPTargetCompletedEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("targetCompleted", event.getEventType());

        var uri = event.getImportantOutput().get(0);
        assertEquals("foo/foo.jar", uri.getId());
        assertEquals(
            "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo.jar",
            uri.getUri().toString());
        assertEquals("bin", uri.getPrefixes().get(2));
        uri = event.getImportantOutput().get(1);
        assertEquals("foo/foo", uri.getId());
        assertEquals(
            "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo",
            uri.getUri().toString());
        assertEquals("bin", uri.getPrefixes().get(2));
    }

}
