package com.salesforce.bazel.eclipse.core;

public interface BazelCoreSharedContstants {

    /** The plug-in identifier of the Bazel Core plug-in */
    String PLUGIN_ID = "com.salesforce.bazel.eclipse.core";

    /** The Bazel project nature */
    String BAZEL_NATURE_ID = "com.salesforce.bazel.eclipse.bazelNature";

    /** The Bazel project builder */
    String BAZEL_BUILDER_ID = "com.salesforce.bazel.eclipse.builder";

    /** Bazel Problem marker */
    String PROBLEM_MARKER = PLUGIN_ID + ".problem";

    /** Build Path related Bazel problem */
    String BUILDPATH_PROBLEM_MARKER = PLUGIN_ID + ".buildpath_problem";

    /** transient Bazel problems, i.e. not persisted across restarts */
    String TRANSIENT_PROBLEM_MARKER = PLUGIN_ID + ".transient_problem";

    /** The source identifier for markers */
    String MARKER_SOURCE_ID = "Bazel";

    /** the Bazel classpath container ID */
    String CLASSPATH_CONTAINER_ID = "com.salesforce.bazel.eclipse.BAZEL_CONTAINER";

}
