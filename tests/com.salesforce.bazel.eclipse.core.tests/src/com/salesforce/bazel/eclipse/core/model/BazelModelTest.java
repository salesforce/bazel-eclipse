package com.salesforce.bazel.eclipse.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

public class BazelModelTest {

    @Test
    void equals_and_hashCode() throws Exception {
        var m1 = new BazelModel(null);
        var m2 = new BazelModel(null);

        assertEquals(m1, m2);
        assertEquals(m2, m1);

        assertEquals(m1.hashCode(), m2.hashCode());
        assertEquals(m2.hashCode(), m1.hashCode());

        assertEquals(m1.hashCode(), BazelModel.class.hashCode());

        assertNotSame(m1, m2);
    }
}
