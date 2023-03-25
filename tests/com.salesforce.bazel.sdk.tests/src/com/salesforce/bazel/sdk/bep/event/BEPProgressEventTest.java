package com.salesforce.bazel.sdk.bep.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

public class BEPProgressEventTest {

    private static final String progressEvent =
            """
                    {
                      "id": {
                        "progress": { "opaqueCount": 5 }
                      },
                      "progress": {
                        "stderr": "ERROR: /Users/mbenioff/dev/myrepo/foo/BUILD:7:12: Building foo/foo-class.jar (59 source files) and running annotation processors (AnnotationProcessorHider$AnnotationProcessor) failed (Exit 1): java failed: error executing command external/remotejdk11_macos/bin/java -XX:+UseParallelOldGC -XX:-CompactStrings '--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED' ... (remaining 15 argument(s) skipped)\\n checking cached actions\\nfoo/src/main/java/com/foo/Foo.java:43: error: <identifier> expected\\n  bar\\n     ^\\n[2 / 6] checking cached actions\\nINFO: Elapsed time: 2.320s, Critical Path: 2.06s\\n[2 / 6] checking cached actions\\nINFO: 2 processes: 2 internal.\\n[2 / 6] checking cached actions\\nFAILED: Build did NOT complete successfully\\n"
                      }
                    }""";

    @Test
    public void testProgressEventParse() throws Exception {
        var rawEvent = progressEvent;
        var index = 5;
        var jsonEvent = (JSONObject) new JSONParser().parse(rawEvent);

        // ctor parses the event, otherwise throws, which is the primary validation here
        var event = new BEPProgressEvent(rawEvent, index, jsonEvent);

        assertTrue(event.isError());
        assertFalse(event.isLastMessage());
        assertFalse(event.isProcessed());
        assertEquals("progress", event.getEventType());

        var stderr = event.getStderr();
        //for (String err : stderr) {
        //    System.out.println(err);
        //}
        assertTrue(stderr.get(0).contains("/Users/mbenioff/dev/myrepo/foo/BUILD:7:12"));
    }

}
