package com.salesforce.bazel.sdk.path;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;

public class FSPathHelperTest {

    // Tests ignored until we work SDK Issue #32 since the static flag breaks when tests run in parallel

    @Test
    @Ignore
    public void test_Unix_Happy() {
        FSPathHelper.isUnix = true;

        var originalPath = "one/two/three/four";
        assertEquals("one/two/three/four", FSPathHelper.osSeps(originalPath));

        originalPath = "one";
        assertEquals("one", FSPathHelper.osSeps(originalPath));
    }

    @Test
    @Ignore
    public void test_Windows_DoubleEscape() {
        FSPathHelper.isUnix = false;

        // we test this because sometimes we may lose track that we have already slash->backslash converted a path,
        // and then need to make it escaped

        var originalPath = "one/two/three/four";
        var convertedPath = FSPathHelper.osSeps(originalPath);
        // convertedPath is now Windows converted "one\two\three\four", but not escaped
        assertEquals("one\\\\two\\\\three\\\\four", FSPathHelper.osSepsEscaped(convertedPath));

        originalPath = "one/two/three\\four";
        convertedPath = FSPathHelper.osSeps(originalPath);
        assertEquals("one\\\\two\\\\three\\\\four", FSPathHelper.osSepsEscaped(convertedPath));
    }

    @Test
    @Ignore
    public void test_Windows_Escape() {
        FSPathHelper.isUnix = false;

        // when putting these paths into some contexts (e.g. json property value) we need to
        // convert each slash into a double-backslash because json would otherwise interpret a single
        // backslash as a control character
        var originalPath = "one/two/three/four";
        assertEquals("one\\\\two\\\\three\\\\four", FSPathHelper.osSepsEscaped(originalPath));
    }

    @Test
    @Ignore
    public void test_Windows_EscapeEscape() {
        FSPathHelper.isUnix = false;

        var originalPath = "one/two/three/four";
        var convertedPath = FSPathHelper.osSepsEscaped(originalPath);
        // convertedPath is now already Windows converted and escaped "one\\two\\three\\four"
        // make sure the escape method doesn't escape again
        assertEquals("one\\\\two\\\\three\\\\four", FSPathHelper.osSepsEscaped(convertedPath));
    }

    @Test
    @Ignore
    public void test_Windows_Happy() {
        FSPathHelper.isUnix = false;

        var originalPath = "one/two/three/four";
        assertEquals("one\\two\\three\\four", FSPathHelper.osSeps(originalPath));

        originalPath = "one";
        assertEquals("one", FSPathHelper.osSeps(originalPath));

        originalPath = "/one/two/three"; // doesn't append a drive designator
        assertEquals("\\one\\two\\three", FSPathHelper.osSeps(originalPath));
    }

}
