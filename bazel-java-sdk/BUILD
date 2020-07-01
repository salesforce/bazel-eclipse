load("//tools:feature_version_def.bzl", "VERSION")

java_library(
    name = "bazel-java-sdk",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [

        # do not add an Eclipse deps here, this library is free from Eclipse API
        # so that we can have easily run unit tests
        # only the core plugin knows about Eclipse API

        "//plugin-libs/plugin-deps:com_google_guava",
        "//plugin-libs/plugin-deps:org_json_json",
    ],
)

# Mock Command library
java_library(
    name = "test-command-mocks",
    srcs = glob([
        "src/test/java/com/salesforce/bazel/eclipse/command/test/*.java",
        "src/test/java/com/salesforce/bazel/eclipse/command/test/type/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        ":bazel-java-sdk",
        ":test-workspace-support",

        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_mockito_mockito_core",
        "//plugin-libs/plugin-deps:com_google_guava",
    ],
)

java_library(
    name = "test-workspace-support",
    srcs = glob(["src/test/java/com/salesforce/bazel/eclipse/workspace/test/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
    ],
)


java_test(
    name = "ArgumentSplitterTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/eclipse/model/ArgumentSplitter.java",
        "src/test/java/com/salesforce/bazel/eclipse/model/ArgumentSplitterTest.java",
    ],
)

java_test(
    name = "AspectPackageInfosTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/model/AspectPackageInfosTest.java",],
    deps = [
        ":bazel-java-sdk",
        "//plugin-libs/plugin-deps:com_google_guava",
    ],
)

java_test(
    name = "BazelLabelTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelLabel.java",
        "src/test/java/com/salesforce/bazel/eclipse/model/BazelLabelTest.java",
    ],
)

java_test(
    name = "BazelProblemTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelProblem.java",
        "src/test/java/com/salesforce/bazel/eclipse/model/BazelProblemTest.java",
    ],
)

java_test(
    name = "BazelOutputDirectoryBuilderTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelOutputDirectoryBuilder.java",
        "src/main/java/com/salesforce/bazel/eclipse/model/TargetKind.java",
        "src/test/java/com/salesforce/bazel/eclipse/model/BazelOutputDirectoryBuilderTest.java"
    ],
)

java_test(
    name = "BazelOutputParserTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelLabel.java",
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelProblem.java",
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelOutputParser.java",
        "src/test/java/com/salesforce/bazel/eclipse/model/BazelOutputParserTest.java",
    ],
    deps = ["//plugin-libs/plugin-deps:com_google_guava",],
)

java_test(
    name = "BazelProjectInfoTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/model/BazelProjectInfoTest.java"],
    deps = [
        ":bazel-java-sdk",

        "//plugin-libs/plugin-deps:com_google_guava",
        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_mockito_mockito_core",
    ],
)

java_test(
    name = "BazelBuildFileHelperTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/model/BazelBuildFileHelperTest.java"],
    deps = [
        ":bazel-java-sdk",

        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)

java_test(
    name = "BazelPackageInfoTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelPackageInfo.java",
        "src/test/java/com/salesforce/bazel/eclipse/model/BazelPackageInfoTest.java",
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelPackageLocation.java",
    ],
)

java_test(
    name = "LogHelperTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/logging/CaptureLoggerFacade.java",
            "src/test/java/com/salesforce/bazel/eclipse/logging/LogHelperTest.java"],
    deps = [
        ":bazel-java-sdk",

        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)

java_test(
    name = "ProjectViewTest",
    srcs = [
        "src/main/java/com/salesforce/bazel/eclipse/model/BazelPackageLocation.java",
        "src/main/java/com/salesforce/bazel/eclipse/model/projectview/ProjectView.java",
        "src/main/java/com/salesforce/bazel/eclipse/model/projectview/ProjectViewPackageLocation.java",
        "src/test/java/com/salesforce/bazel/eclipse/model/projectview/ProjectViewTest.java",
    ],
)

java_test(
    name = "BazelWorkspaceTest",
    srcs = glob([
        "src/test/java/com/salesforce/bazel/eclipse/model/**/*.java",
    ]),
    deps = [
        ":bazel-java-sdk",

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
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/abstractions/BazelCommandArgsTest.java"],
    deps = [
        ":bazel-java-sdk",

        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:com_google_truth",
    ],
)



java_test(
    name = "BazelLauncherBuilderTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/eclipse/command/BazelLauncherBuilderTest.java",
    ],
    deps = [
        ":bazel-java-sdk",
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
    name = "BazelWorkspaceCommandRunnerTest",
    srcs = [
       "src/test/java/com/salesforce/bazel/eclipse/command/BazelWorkspaceCommandRunnerTest.java",
    ],
    deps = [
        ":bazel-java-sdk",
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
       "src/test/java/com/salesforce/bazel/eclipse/command/ShellCommandTest.java",
    ],
    deps = [
        ":bazel-java-sdk",

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
        ":bazel-java-sdk",
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
       "src/test/java/com/salesforce/bazel/eclipse/command/internal/BazelCommandExecutorTest.java",
    ],
    deps = [
        ":bazel-java-sdk",
        ":test-command-mocks",
        ":test-workspace-support",

        "//plugin-libs/plugin-testdeps:com_google_truth",
        "//plugin-libs/plugin-testdeps:net_bytebuddy_byte_buddy",
        "//plugin-libs/plugin-testdeps:org_hamcrest_core",
        "//plugin-libs/plugin-testdeps:org_junit_junit",
        "//plugin-libs/plugin-testdeps:org_objenesis_objenesis",
    ],
)
