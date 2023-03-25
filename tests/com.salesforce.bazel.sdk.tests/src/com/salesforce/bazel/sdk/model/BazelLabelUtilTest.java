package com.salesforce.bazel.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.junit.Test;

public class BazelLabelUtilTest {

    @Test
    public void testGroupByPackage() {
        var l1 = new BazelLabel("a/b/c:t1"); // $SLASH_OK bazel path
        var l2 = new BazelLabel("d/e/f:t2"); // $SLASH_OK bazel path
        var l3 = new BazelLabel("a/b/c:t2"); // $SLASH_OK bazel path

        var m = BazelLabelUtil.groupByPackage(Arrays.asList(l1, l2, l3));

        assertEquals(2, m.size());
        assertTrue(m.get(new BazelLabel("a/b/c")).contains(l1)); // $SLASH_OK bazel path
        assertTrue(m.get(new BazelLabel("a/b/c")).contains(l3)); // $SLASH_OK bazel path
        assertTrue(m.get(new BazelLabel("d/e/f")).contains(l2)); // $SLASH_OK bazel path
    }

}
