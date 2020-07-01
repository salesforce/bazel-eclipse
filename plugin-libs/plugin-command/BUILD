load(
    "//tools/eclipse:eclipse_plugin.bzl",
    "eclipse_plugin",
)
load("//tools:feature_version_def.bzl", "VERSION")

java_library(
    name = "plugin-command",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        # do not add an Eclipse deps here, this library is free from Eclipse API
        # so that we can have easily run unit tests
        # only the core plugin knows about Eclipse API

        "//plugin-libs/plugin-model",

        "//plugin-libs/plugin-deps:com_google_guava",
    ],
)

eclipse_plugin(
    name = "com.salesforce.bazel.eclipse.command",
    srcs = glob(["src/main/java/**/*.java"]),
    bundle_name = "Bazel Eclipse Command Plugin",
    vendor = "Salesforce, Bazel Authors",
    version = VERSION,
    exports = 'com.salesforce.bazel.eclipse.command;version="'+VERSION+'"',
    visibility = ["//visibility:public"],
    deps = [
    "//plugin-libs/plugin-model",

    "//plugin-libs/plugin-deps:com_google_guava",
    ],
)

# Mock Command library
java_library(
    name = "plugin-command-test-mocks",
    srcs = glob([
        "src/test/java/com/salesforce/bazel/eclipse/command/mock/*.java",
        "src/test/java/com/salesforce/bazel/eclipse/command/mock/type/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        ":plugin-command",
        "//plugin-libs/plugin-model",
        "//plugin-libs/plugin-testdeps",

        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_mockito_mockito_core",
        "//plugin-libs/plugin-deps:com_google_guava",
    ],
)


java_test(
    name = "BazelLauncherBuilderTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/eclipse/command/BazelLauncherBuilderTest.java",
    ],
    deps = [
        ":plugin-command",
        ":plugin-command-test-mocks",
        "//plugin-libs/plugin-model",
        "//plugin-libs/plugin-testdeps",

        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:net_bytebuddy_byte_buddy",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_objenesis_objenesis",
    ],
)

java_test(
    name = "BazelWorkspaceCommandRunnerTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/eclipse/command/BazelWorkspaceCommandRunnerTest.java",
    ],
    deps = [
        ":plugin-command",
        ":plugin-command-test-mocks",
        "//plugin-libs/plugin-model",
        "//plugin-libs/plugin-testdeps",

        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:net_bytebuddy_byte_buddy",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_objenesis_objenesis",
    ],
)

java_test(
    name = "ShellCommandTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/eclipse/command/ShellCommandTest.java",
    ],
    deps = [
        ":plugin-command",
        "//plugin-libs/plugin-model",
        
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)

java_test(
    name = "BazelWorkspaceAspectHelperTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/eclipse/command/internal/BazelWorkspaceAspectHelperTest.java",
    ],
    deps = [
        ":plugin-command",
        ":plugin-command-test-mocks",
        "//plugin-libs/plugin-model",
        "//plugin-libs/plugin-testdeps",

        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:net_bytebuddy_byte_buddy",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_objenesis_objenesis",
    ],
)

java_test(
    name = "BazelCommandExecutorTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/eclipse/command/internal/BazelCommandExecutorTest.java",
    ],
    deps = [
        ":plugin-command",
        ":plugin-command-test-mocks",
        "//plugin-libs/plugin-testdeps",

        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:net_bytebuddy_byte_buddy",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_objenesis_objenesis",
    ],
)
