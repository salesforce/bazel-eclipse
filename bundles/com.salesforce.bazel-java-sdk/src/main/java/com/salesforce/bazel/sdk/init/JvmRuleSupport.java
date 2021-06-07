package com.salesforce.bazel.sdk.init;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfoFactory;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfoFactoryProvider;
import com.salesforce.bazel.sdk.model.BazelTargetKind;

/**
 * Initializer to install support for Java rules into the SDK. Call initialize() once
 * at startup.
 */
public class JvmRuleSupport {

    // register the collection of Java rules that we want to handle
    public static final BazelTargetKind KIND_JAVA_LIBRARY = new BazelTargetKind("java_library", false, false);
    public static final BazelTargetKind KIND_JAVA_TEST = new BazelTargetKind("java_test", false, true);
    public static final BazelTargetKind KIND_JAVA_BINARY = new BazelTargetKind("java_binary", true, false);
    public static final BazelTargetKind KIND_SELENIUM_TEST = new BazelTargetKind("java_web_test_suite", false, true);
    public static final BazelTargetKind KIND_SPRINGBOOT = new BazelTargetKind("springboot", true, false);
    public static final BazelTargetKind KIND_PROTO_LIBRARY = new BazelTargetKind("java_proto_library", false, false);
    public static final BazelTargetKind KIND_PROTO_LITE_LIBRARY = new BazelTargetKind("java_lite_proto_library", false, false);
    public static final BazelTargetKind KIND_GRPC_LIBRARY = new BazelTargetKind("java_grpc_library", false, false);
    
    /**
     * Call once at the start of the tool to initialize the JVM rule support.
     */
    public static void initialize() {
        AspectTargetInfoFactory.addProvider(new JVMAspectTargetInfoFactoryProvider());
    }

}
