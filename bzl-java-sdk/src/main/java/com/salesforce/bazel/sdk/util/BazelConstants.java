package com.salesforce.bazel.sdk.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

public interface BazelConstants {

    Collection<String> BUILD_FILE_NAMES =
        Collections.unmodifiableSet(
            new HashSet<>(
                Arrays.asList(
                    new String[]{"BUILD", "BUILD.bazel"})));

}
