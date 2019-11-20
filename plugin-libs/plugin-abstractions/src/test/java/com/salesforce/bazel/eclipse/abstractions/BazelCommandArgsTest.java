package com.salesforce.bazel.eclipse.abstractions;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BazelCommandArgsTest {
    @Test
    public void testArgs() {
        assertEquals("--test_filter", BazelCommandArgs.TEST_FILTER.getName());
    }
}
