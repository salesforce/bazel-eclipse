package com.salesforce.bazel.eclipse.core;

public interface BazelCoreSharedContstants {

    /** The plug-in identifier of the Bazel Core plug-in */
    String PLUGIN_ID = "com.salesforce.bazel.eclipse.core";

    /** The Bazel project nature */
    String BAZEL_NATURE_ID = "com.salesforce.bazel.eclipse.core.bazelnature";

    /** The Bazel project builder */
    String BAZEL_BUILDER_ID = "com.salesforce.bazel.eclipse.core.bazelprojectbuilder";

    /** Bazel Problem marker */
    String PROBLEM_MARKER = PLUGIN_ID + ".problem";

    /** Build Path related Bazel problem */
    String BUILDPATH_PROBLEM_MARKER = PLUGIN_ID + ".buildpath_problem";

    /** Classpath Container related Bazel problem */
    String CLASSPATH_CONTAINER_PROBLEM_MARKER = PLUGIN_ID + ".container_problem";

    /** transient Bazel problems, i.e. not persisted across restarts */
    String TRANSIENT_PROBLEM_MARKER = PLUGIN_ID + ".transient_problem";

    /** The source identifier for markers */
    String MARKER_SOURCE_ID = "Bazel";

    /** the Bazel classpath container ID */
    String CLASSPATH_CONTAINER_ID = "com.salesforce.bazel.eclipse.BAZEL_CONTAINER";

    /** {@code REPO.bazel} */
    String FILE_NAME_REPO_BAZEL = "REPO.bazel";

    /** {@code MODULE.bazel} */
    String FILE_NAME_MODULE_BAZEL = "MODULE.bazel";

    /** {@code WORKSPACE.bazel} */
    String FILE_NAME_WORKSPACE_BAZEL = "WORKSPACE.bazel";

    /** {@code WORKSPACE} */
    String FILE_NAME_WORKSPACE = "WORKSPACE";

    /** {@code BUILD.bazel} */
    String FILE_NAME_BUILD_BAZEL = "BUILD.bazel";

    /** {@code BUILD} */
    String FILE_NAME_BUILD = "BUILD";

    /** {@code .bazelproject} */
    String FILE_NAME_DOT_BAZELPROJECT = ".bazelproject";

    /** id of the resource filter responsible for filtering bazel symlinks from the workspace project */
    String RESOURCE_FILTER_BAZEL_OUTPUT_SYMLINKS_ID =
            "com.salesforce.bazel.eclipse.core.resources.filter.bazelOutputMatcher";

    /** tag on targets when they are not supposed to show up in IDEs */
    String TAG_NO_IDE = "no-ide";

}
