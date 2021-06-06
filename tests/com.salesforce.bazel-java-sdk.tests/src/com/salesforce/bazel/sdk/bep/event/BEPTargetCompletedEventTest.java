package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPTargetCompletedEventTest {

    private static final String successEvent = "{\n" + "  \"id\": {\n" + "    \"targetCompleted\": {\n"
            + "      \"label\": \"//foo:foo\",\n"
            + "      \"configuration\": { \"id\": \"63cc040ed2b86a512099924e698df6e0b9848625e6ca33d9556c5993dccbc2fb\" }\n"
            + "    }\n" + "  },\n" + "  \"completed\": {\n" + "    \"success\": true,\n" + "    \"outputGroup\": [\n"
            + "      {\n" + "        \"name\": \"default\",\n" + "        \"fileSets\": [ { \"id\": \"2\" } ]\n"
            + "      }\n" + "    ],\n" + "    \"importantOutput\": [\n" + "      {\n"
            + "        \"name\": \"foo/foo.jar\",\n"
            + "        \"uri\": \"file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo.jar\",\n"
            + "        \"pathPrefix\": [\n" + "          \"bazel-out\", \"darwin-fastbuild\", \"bin\"\n" + "        ]\n"
            + "      },\n" + "      {\n" + "        \"name\": \"foo/foo\",\n"
            + "        \"uri\": \"file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/bin/foo/foo\",\n"
            + "        \"pathPrefix\": [\n" + "          \"bazel-out\", \"darwin-fastbuild\", \"bin\"\n" + "        ]\n"
            + "      }\n" + "    ]\n" + "  }\n" + "}";

    @Test
    public void testSuccessEventParse() throws Exception {
        String rawEvent = successEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPTargetCompletedEvent event = new BEPTargetCompletedEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("targetCompleted", event.getEventType());

        BEPFileUri uri = event.getImportantOutput().get(0);
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
