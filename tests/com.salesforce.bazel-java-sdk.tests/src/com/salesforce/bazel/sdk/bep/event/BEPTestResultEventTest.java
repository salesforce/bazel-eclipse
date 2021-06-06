package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPTestResultEventTest {

    private static final String resultEvent = "{\n" + "  \"id\": {\n" + "    \"testResult\": {\n"
            + "      \"label\": \"//foo:foo-test\",\n" + "      \"run\": 1,\n" + "      \"shard\": 1,\n"
            + "      \"attempt\": 1,\n"
            + "      \"configuration\": { \"id\": \"63cc040ed2b86a512099924e698df6e0b9848625e6ca33d9556c5993dccbc2fb\" }\n"
            + "    }\n" + "  },\n" + "  \"testResult\": {\n" + "    \"testActionOutput\": [\n" + "      {\n"
            + "        \"name\": \"test.log\",\n"
            + "        \"uri\": \"file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log\"\n"
            + "      },\n" + "      {\n" + "        \"name\": \"test.xml\",\n"
            + "        \"uri\": \"file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.xml\"\n"
            + "      }\n" + "    ],\n" + "    \"testAttemptDurationMillis\": \"826\",\n"
            + "    \"status\": \"FAILED\",\n" + "    \"testAttemptStartMillisEpoch\": \"1622353495424\",\n"
            + "    \"executionInfo\": { \"strategy\": \"darwin-sandbox\" }\n" + "  }\n" + "}";

    @Test
    public void testResultEventParse() throws Exception {
        String rawEvent = resultEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPTestResultEvent event = new BEPTestResultEvent(rawEvent, index, jsonEvent);

        assertTrue(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("testResult", event.getEventType());

        BEPFileUri uri = event.getActionOutputs().get("test.log");
        assertEquals("test.log", uri.getId());
        assertEquals(
            "file:///private/var/tmp/_bazel_mbenioff/8fc74f66fda297c82a847368ee50d6a4/execroot/myrepo/bazel-out/darwin-fastbuild/testlogs/foo/foo-test/test.log",
            uri.getUri().toString());
    }

}
