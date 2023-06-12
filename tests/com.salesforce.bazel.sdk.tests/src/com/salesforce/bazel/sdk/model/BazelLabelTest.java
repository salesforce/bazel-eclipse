package com.salesforce.bazel.sdk.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BazelLabelTest {

    @Test
    void isConcrete_positive_test() {
        assertTrue(new BazelLabel("//src/main/java/com/google/devtools/build/lib/runtime/mobileinstall:mobileinstall")
                .isConcrete());
    }

}
