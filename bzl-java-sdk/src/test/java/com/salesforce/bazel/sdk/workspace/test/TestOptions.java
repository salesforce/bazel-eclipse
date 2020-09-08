package com.salesforce.bazel.sdk.workspace.test;

import java.util.HashMap;

/**
 * Holder of options that a test has set. Options are used by various elements of the mocking framework to allow you to
 * alter the way the mocks behave. The TestOptions object is passed around the layers and is available to most of the
 * Mock objects.
 * <p>
 * This is its own custom type to make finding example usages easier, as the various Mock objects are only documented in
 * code.
 */
public class TestOptions extends HashMap<String, String> {
    private static final long serialVersionUID = 1L;

    /**
     * Mock code that supports a test option should call this method with that option name. This is here to help you
     * find all the various options available throughout the code base. Just look for usages of this method.
     */
    public static void advertise(String optionName) {}
}
