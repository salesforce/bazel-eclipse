package com.salesforce.bazel.eclipse.core;

public interface BazelCorePluginSharedContstants {

    /** The plug-in identifier of the Bazel Core plug-in */
    String PLUGIN_ID = "com.salesforce.bazel.eclipse.core";

    /** The Bazel project nature */
    String BAZEL_NATURE_ID = "com.salesforce.bazel.eclipse.bazelNature";

    String PROBLEM_MARKER = PLUGIN_ID + ".problem";
    String BUILDPATH_PROBLEM_MARKER = PLUGIN_ID + ".buildpath_problem";
    String TRANSIENT_PROBLEM_MARKER = PLUGIN_ID + ".transient_problem";

    /** The source identifier for markers */
    String MARKER_SOURCE_ID = "Bazel";

}
