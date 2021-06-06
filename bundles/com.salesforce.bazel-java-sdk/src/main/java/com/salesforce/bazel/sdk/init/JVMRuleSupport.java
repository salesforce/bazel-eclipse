package com.salesforce.bazel.sdk.init;

import com.salesforce.bazel.sdk.model.BazelTargetKind;

public class JVMRuleSupport {

    public static final BazelTargetKind KIND_JAVA_LIBRARY = new BazelTargetKind("java_library", false, false);
    public static final BazelTargetKind KIND_JAVA_TEST = new BazelTargetKind("java_test", false, true);
    public static final BazelTargetKind KIND_JAVA_BINARY = new BazelTargetKind("java_binary", true, false);
    public static final BazelTargetKind KIND_SELENIUM_TEST = new BazelTargetKind("java_web_test_suite", false, true);
    
    public static void initialize() {
        // static initialization happens when this class is loaded, no further setup is required
    }

}
