package com.salesforce.bazel.eclipse.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.Test;

public class BazelWorkspaceTest {

    @Test
    void equals_and_hashCode() throws Exception {
        var m = new BazelModel(null);

        var r1 = new Path("/root1");
        var r2 = new Path("/root2");

        var w_r1 = new BazelWorkspace(r1, m);
        var w_r2 = new BazelWorkspace(r2, m);

        assertEquals(w_r1, new BazelWorkspace(r1, m));
        assertEquals(w_r2, new BazelWorkspace(r2, m));

        assertEquals(w_r1.hashCode(), new BazelWorkspace(r1, m).hashCode());
        assertEquals(w_r2.hashCode(), new BazelWorkspace(r2, m).hashCode());

        assertNotSame(w_r1, new BazelWorkspace(r1, m));
        assertNotSame(w_r2, new BazelWorkspace(r2, m));

        assertNotEquals(w_r1, w_r2);
        assertNotEquals(w_r1.hashCode(), w_r2.hashCode());
    }

}
