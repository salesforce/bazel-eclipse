package com.salesforce.bazel.sdk.model;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.junit.Test;

public class BazelLabelUtilTest {

    @Test
    public void testGroupByPackage() {
        BazelLabel l1 = new BazelLabel("a/b/c:t1");
        BazelLabel l2 = new BazelLabel("d/e/f:t2");
        BazelLabel l3 = new BazelLabel("a/b/c:t2");

        Map<BazelLabel, Collection<BazelLabel>> m = BazelLabelUtil.groupByPackage(Arrays.asList(new BazelLabel[]{l1, l2, l3}));

        assertEquals(2, m.size());
        assertThat(m.get(new BazelLabel("a/b/c")), hasItem(l1));
        assertThat(m.get(new BazelLabel("a/b/c")), hasItem(l3));
        assertThat(m.get(new BazelLabel("d/e/f")), hasItem(l2));
    }

}
