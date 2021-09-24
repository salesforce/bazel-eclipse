package com.salesforce.bazel.eclipse.config;

public interface IEclipseBazelProjectSettings {
    /**
     * Absolute path of the Bazel workspace root
     */
    String BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY = "bazel.workspace.root";
    /**
     * The label that identifies the Bazel package that represents this Eclipse project. This will be the 'module' label
     * when we start supporting multiple BUILD files in a single 'module'.
     * <p>
     * Example: //projects/libs/foo ($SLASH_OK bazel path) See https://github.com/salesforce/bazel-eclipse/issues/24
     * ($SLASH_OK url)
     */
    String PROJECT_PACKAGE_LABEL = "bazel.package.label";
    /**
     * After import, the activated target is a single line, like: bazel.activated.target0=//projects/libs/foo:*
     * ($SLASH_OK bazel path) which activates all targets by use of the wildcard. But users may wish to activate a
     * subset of the targets for builds, in which the prefs lines will look like:
     * bazel.activated.target0=//projects/libs/foo:barlib bazel.activated.target1=//projects/libs/foo:bazlib
     */
    String TARGET_PROPERTY_PREFIX = "bazel.activated.target";

    /**
     * Property that allows a user to set project specific build flags that get passed to the Bazel executable.
     */
    String BUILDFLAG_PROPERTY_PREFIX = "bazel.build.flag";

}
