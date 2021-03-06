package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPUnstructuredCommandLineEventTest {

    private static final String commandEvent = "{\n" + "  \"id\": { \"unstructuredCommandLine\": {} },\n"
            + "  \"unstructuredCommandLine\": {\n" + "    \"args\": [\n" + "      \"build\",\n"
            + "      \"--binary_path=/Users/mbenioff/Library/Caches/bazelisk/downloads/bazelbuild/bazel-3.7.1-darwin-x86_64/bin/bazel\",\n"
            + "      \"--rc_source=/Users/mbenioff/dev/sfdc-bazel/.bazelrc\",\n"
            + "      \"--default_override=2:run=--action_env=PATH\",\n"
            + "      \"--default_override=1:test:debug=--test_arg=--node_options=--inspect-brk\",\n"
            + "      \"--default_override=2:test=--explicit_java_test_deps=true\",\n"
            + "      \"--default_override=2:build=--javacopt=--release 11\",\n"
            + "      \"--client_env=TERM_PROGRAM=Apple_Terminal\",\n" + "      \"--client_env=USER=mbenioff\",\n"
            + "      \"--client_env=PWD=/Users/mbenioff/dev/myrepo\",\n"
            + "      \"--client_env=JAVA_HOME=/Users/mbenioff/java/openjdk_11.0.9_11.43.54_x64\",\n"
            + "      \"//...\"\n" + "    ]\n" + "  }\n" + "}";

    @Test
    public void testPatternEventParse() throws Exception {
        String rawEvent = commandEvent;
        int index = 5;
        JSONObject jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        BEPUnstructuredCommandLineEvent event = new BEPUnstructuredCommandLineEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("unstructuredCommandLine", event.getEventType());

        assertEquals(12, event.getArgs().size());
        assertEquals("build", event.getArgs().get(0));
        assertEquals("//...", event.getArgs().get(11));
    }

}
