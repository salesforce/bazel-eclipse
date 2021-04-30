load("//tools:feature_version_def.bzl", "VERSION")

java_library(
    name = "bzl-java-sdk",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//plugin-libs/plugin-deps:com_google_guava",
        "//plugin-libs/plugin-deps:org_json_json",
    ],
)

# Mock Command library
java_library(
    name = "test-command-mocks",
    srcs = glob([
        "src/test/java/com/salesforce/bazel/sdk/command/test/*.java",
        "src/test/java/com/salesforce/bazel/sdk/command/test/type/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        ":bzl-java-sdk",
        ":test-workspace-support",

        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_mockito_mockito_core",
        "//plugin-libs/plugin-deps:com_google_guava",
    ],
)

java_library(
    name = "test-workspace-support",
    srcs = glob(["src/test/java/com/salesforce/bazel/sdk/workspace/test/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
    ],
)


java_test(
    name = "ArgumentSplitterTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/sdk/command/ArgumentSplitter.java",
        "src/test/java/com/salesforce/bazel/sdk/command/ArgumentSplitterTest.java",
    ],
)

java_test(
    name = "AspectTargetInfosTest",
    srcs = ["src/test/java/com/salesforce/bazel/sdk/aspect/AspectTargetInfosTest.java",],
    deps = [
        ":bzl-java-sdk",
        "//plugin-libs/plugin-deps:com_google_guava",
    ],
)

java_test(
    name = "BazelLabelTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/sdk/model/BazelLabel.java",
        "src/test/java/com/salesforce/bazel/sdk/model/BazelLabelTest.java",
    ],
)

java_test(
    name = "BazelLabelUtilTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/sdk/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/sdk/model/BazelLabelUtil.java",
        "src/test/java/com/salesforce/bazel/sdk/model/BazelLabelUtilTest.java",
    ],
)

java_test(
    name = "BazelProblemTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/sdk/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/sdk/model/BazelProblem.java",
        "src/test/java/com/salesforce/bazel/sdk/model/BazelProblemTest.java",
    ],
)

java_test(
    name = "BazelOutputDirectoryBuilderTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/sdk/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/sdk/model/BazelTargetKind.java",
        "src/main/java/com/salesforce/bazel/sdk/command/BazelOutputDirectoryBuilder.java",
        "src/test/java/com/salesforce/bazel/sdk/command/BazelOutputDirectoryBuilderTest.java"
    ],
)

java_test(
    name = "BazelOutputParserTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/sdk/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/sdk/model/BazelProblem.java",
        "src/main/java/com/salesforce/bazel/sdk/command/BazelOutputParser.java",
        "src/test/java/com/salesforce/bazel/sdk/command/BazelOutputParserTest.java",
    ],
    deps = ["//plugin-libs/plugin-deps:com_google_guava",],
)

java_test(
    name = "BazelProjectInfoTest",
    srcs = ["src/test/java/com/salesforce/bazel/sdk/model/BazelProjectInfoTest.java"],
    deps = [
        ":bzl-java-sdk",

        "//plugin-libs/plugin-deps:com_google_guava",
        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_mockito_mockito_core",
    ],
)

java_test(
    name = "BazelBuildFileHelperTest",
    srcs = ["src/test/java/com/salesforce/bazel/sdk/model/BazelBuildFileHelperTest.java"],
    deps = [
        ":bzl-java-sdk",

        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)

java_test(
    name = "BazelPackageInfoTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/sdk/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/sdk/model/BazelPackageInfo.java",
        "src/test/java/com/salesforce/bazel/sdk/model/BazelPackageInfoTest.java",
        "src/main/java/com/salesforce/bazel/sdk/model/BazelPackageLocation.java",
    ],
)

java_test(
    name = "LogHelperTest",
    srcs = ["src/test/java/com/salesforce/bazel/sdk/logging/CaptureLoggerFacade.java",
            "src/test/java/com/salesforce/bazel/sdk/logging/LogHelperTest.java"],
    deps = [
        ":bzl-java-sdk",

        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)

java_test(
    name = "ProjectViewTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/sdk/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/sdk/model/BazelPackageLocation.java",
        "src/main/java/com/salesforce/bazel/sdk/project/ProjectView.java",
        "src/main/java/com/salesforce/bazel/sdk/project/ProjectViewPackageLocation.java",
        "src/test/java/com/salesforce/bazel/sdk/project/ProjectViewTest.java",
        "src/main/java/com/salesforce/bazel/sdk/util/BazelConstants.java",
    ],
)

java_test(
    name = "BazelWorkspaceTest",
    srcs = glob([
        "src/test/java/com/salesforce/bazel/sdk/model/**/*.java",
    ]),
    deps = [
        ":bzl-java-sdk",

        "//plugin-libs/plugin-deps:com_google_guava",
        "//plugin-libs/plugin-deps:org_json_json",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_mockito_mockito_core",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)

java_test(
    name = "BazelCommandArgsTest",
    srcs = ["src/test/java/com/salesforce/bazel/sdk/command/BazelCommandArgsTest.java"],
    deps = [
        ":bzl-java-sdk",

        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)



java_test(
    name = "BazelLauncherBuilderTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/sdk/command/BazelLauncherBuilderTest.java",
    ],
    deps = [
        ":bzl-java-sdk",
        ":test-command-mocks",
        ":test-workspace-support",

        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:net_bytebuddy_byte_buddy",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_objenesis_objenesis",
    ],
)

java_test(
    name = "BazelWorkspaceScannerTest",
    srcs = ["src/test/java/com/salesforce/bazel/sdk/workspace/BazelWorkspaceScannerTest.java"],
    deps = [
        ":bzl-java-sdk",
        ":test-workspace-support",
    ],
    runtime_deps = [
        "//tools/eclipse_jars:org_eclipse_core_jobs",
        "//tools/eclipse_jars:org_eclipse_core_resources",
        "//tools/eclipse_jars:org_eclipse_equinox_common",
        "//tools/eclipse_jars:org_eclipse_ui_ide",
    ],
)


java_test(
    name = "BazelWorkspaceCommandRunnerTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/sdk/command/BazelWorkspaceCommandRunnerTest.java",
    ],
    deps = [
        ":bzl-java-sdk",
        ":test-command-mocks",
        ":test-workspace-support",

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
       "src/test/java/com/salesforce/bazel/sdk/command/ShellCommandTest.java",
    ],
    deps = [
        ":bzl-java-sdk",

        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)

java_test(
    name = "BazelWorkspaceAspectHelperTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/sdk/command/internal/BazelWorkspaceAspectHelperTest.java",
    ],
    deps = [
        ":bzl-java-sdk",
        ":test-command-mocks",
        ":test-workspace-support",

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
       "src/test/java/com/salesforce/bazel/sdk/command/internal/BazelCommandExecutorTest.java",
    ],
    deps = [
        ":bzl-java-sdk",
        ":test-command-mocks",
        ":test-workspace-support",

        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:net_bytebuddy_byte_buddy",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_objenesis_objenesis",
    ],
)
