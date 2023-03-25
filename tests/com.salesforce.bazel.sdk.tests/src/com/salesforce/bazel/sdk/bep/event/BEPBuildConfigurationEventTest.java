package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPBuildConfigurationEventTest {

    private static final String configEvent = """
            {
              "id": {
                "configuration": { "id": "63cc040ed2b86a512099924e698df6e0b9848625e6ca33d9556c5993dccbc2fb" }
              },
              "configuration": {
                "mnemonic": "darwin-fastbuild",
                "platformName": "darwin",
                "cpu": "darwin",
                "makeVariable": {
                  "COMPILATION_MODE": "fastbuild",
                  "TARGET_CPU": "darwin",
                  "GENDIR": "bazel-out/darwin-fastbuild/bin",
                  "BINDIR": "bazel-out/darwin-fastbuild/bin"
                }
              }
            }""";

    @Test
    public void testConfigEventParse() throws Exception {
        var rawEvent = configEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPConfigurationEvent(rawEvent, index, jsonEvent);

        assertFalse(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("configuration", event.getEventType());

        assertEquals("darwin-fastbuild", event.getMnemonic());
        assertEquals("darwin", event.getPlatformName());
        assertEquals("darwin", event.getCpu());
        var value = event.getMakeVariables().get("COMPILATION_MODE");
        assertEquals("fastbuild", value);
        value = event.getMakeVariables().get("TARGET_CPU");
        assertEquals("darwin", value);
        value = event.getMakeVariables().get("GENDIR");
        assertEquals("bazel-out/darwin-fastbuild/bin", value);
        value = event.getMakeVariables().get("BINDIR");
        assertEquals("bazel-out/darwin-fastbuild/bin", value);
    }

}
