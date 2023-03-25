package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPUnstructuredCommandLineEventTest {

    private static final String commandEvent =
            """
                    {
                      "id": { "unstructuredCommandLine": {} },
                      "unstructuredCommandLine": {
                        "args": [
                          "build",
                          "--binary_path=/Users/mbenioff/Library/Caches/bazelisk/downloads/bazelbuild/bazel-3.7.1-darwin-x86_64/bin/bazel",
                          "--rc_source=/Users/mbenioff/dev/sfdc-bazel/.bazelrc",
                          "--default_override=2:run=--action_env=PATH",
                          "--default_override=1:test:debug=--test_arg=--node_options=--inspect-brk",
                          "--default_override=2:test=--explicit_java_test_deps=true",
                          "--default_override=2:build=--javacopt=--release 11",
                          "--client_env=TERM_PROGRAM=Apple_Terminal",
                          "--client_env=USER=mbenioff",
                          "--client_env=PWD=/Users/mbenioff/dev/myrepo",
                          "--client_env=JAVA_HOME=/Users/mbenioff/java/openjdk_11.0.9_11.43.54_x64",
                          "//..."
                        ]
                      }
                    }""";

    @Test
    public void testPatternEventParse() throws Exception {
        var rawEvent = commandEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPUnstructuredCommandLineEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("unstructuredCommandLine", event.getEventType());

        assertEquals(12, event.getArgs().size());
        assertEquals("build", event.getArgs().get(0));
        assertEquals("//...", event.getArgs().get(11));
    }

}
