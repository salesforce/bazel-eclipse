load(
    "//tools/eclipse:eclipse_plugin.bzl",
    "eclipse_plugin",
)
load("//tools:feature_version_def.bzl", "VERSION")


eclipse_plugin(
    name = "com.salesforce.bazel.eclipse.core",
    srcs = glob(["src/main/java/**/*.java"]),
    activator = "com.salesforce.bazel.eclipse.BazelPluginActivator",
    bundle_name = "Bazel Eclipse Core Plugin",
    resources = ["//plugin-core/resources:srcs"],
    vendor = "Salesforce, Bazel Authors",
    version = VERSION,
    visibility = ["//visibility:public"],
    # include this p2.inf entry only if you want to manually control the contents of this file, otherwise it is auto generated
    classpath_resources = ['plugin.xml'],
    deps = [
        "//plugin-libs/plugin-model",

        "//plugin-libs/plugin-deps:com_google_guava",
    ],
)

#
# Tests
# When adding tests, it can be a little challenging to diagnose ClassNotFound errors. If the
# class that is not found extends an Eclipse class, you will need to find the Eclipse library
# that contains that base class, and add it to the deps member. Sometimes deps are only needed at
# runtime, in which case you can add them just as runtime_deps
#

# Mocking Framework
mock_src = glob(["src/test/java/com/salesforce/bazel/eclipse/mock/*.java"])
mock_deps = [
        "//plugin-libs/plugin-testdeps",
        "//plugin-libs/plugin-testdeps:org_mockito_mockito_core",
        "//plugin-libs/plugin-model:plugin-model-test-mocks",

        "//plugin-libs/plugin-deps:com_google_guava",

        "//tools/eclipse_jars:org_eclipse_core_contenttype",
        "//tools/eclipse_jars:org_eclipse_core_jobs",
        "//tools/eclipse_jars:org_eclipse_core_resources",
        "//tools/eclipse_jars:org_eclipse_core_runtime",
        "//tools/eclipse_jars:org_eclipse_debug_core",
        "//tools/eclipse_jars:org_eclipse_equinox_common",
        "//tools/eclipse_jars:org_eclipse_equinox_preferences",
        "//tools/eclipse_jars:org_eclipse_equinox_registry",
        "//tools/eclipse_jars:org_eclipse_jdt_core",
        "//tools/eclipse_jars:org_eclipse_jface",
        "//tools/eclipse_jars:org_eclipse_osgi",
        "//tools/eclipse_jars:org_eclipse_ui_ide",
        "//tools/eclipse_jars:org_eclipse_ui_workbench",
]
mock_runtime_deps = [
        "//plugin-libs/plugin-testdeps:net_bytebuddy_byte_buddy",
        "//plugin-libs/plugin-testdeps:org_objenesis_objenesis",

        # because this is Java, and we only need a few SWT classes to load, not actually run, this works cross platform
        "//tools/eclipse_jars:org_eclipse_swt_cocoa_macosx_x86_64",
]

java_test(
    name = "BazelRuntimeClasspathProviderTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/launch/BazelRuntimeClasspathProviderTest.java"],
    deps = [
        ":com.salesforce.bazel.eclipse.core-bin",
        "//plugin-libs/plugin-model",

        "//tools/eclipse_jars:org_eclipse_equinox_common",
        "//tools/eclipse_jars:org_eclipse_jdt_launching",
        "//tools/eclipse_jars:org_eclipse_osgi",
    ],
    runtime_deps = [
        "//tools/eclipse_jars:org_eclipse_equinox_preferences",
    ]
)

java_test(
    name = "BazelLaunchConfigurationDelegateFTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/launch/BazelLaunchConfigurationDelegateFTest.java"] + mock_src,
    deps = [
        ":com.salesforce.bazel.eclipse.core-bin",

        "//plugin-libs/plugin-model",
    ] + mock_deps,
    runtime_deps = mock_runtime_deps,
 )

java_test(
    name = "BazelProjectImportScannerTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/importer/BazelProjectImportScannerTest.java"],
    deps = [
        ":com.salesforce.bazel.eclipse.core-bin",
        "//plugin-libs/plugin-model",

        "//plugin-libs/plugin-testdeps",
    ],
    runtime_deps = [
        "//tools/eclipse_jars:org_eclipse_core_jobs",
        "//tools/eclipse_jars:org_eclipse_core_resources",
        "//tools/eclipse_jars:org_eclipse_equinox_common",
        "//tools/eclipse_jars:org_eclipse_ui_ide",
    ],
)

java_test(
    name = "BazelCommandRunnerFTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/command/BazelCommandRunnerFTest.java"] + mock_src,
    deps = [
        ":com.salesforce.bazel.eclipse.core-bin",
        "//plugin-libs/plugin-model",
    ] + mock_deps,
    runtime_deps = mock_runtime_deps,
)

java_test(
    name = "BazelEclipseProjectFactoryFTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/config/BazelEclipseProjectFactoryFTest.java"] + mock_src,
    deps = [
        ":com.salesforce.bazel.eclipse.core-bin",
        "//plugin-libs/plugin-model",
    ] + mock_deps,
    runtime_deps = mock_runtime_deps,
)

java_test(
    name = "BazelClasspathContainerFTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/classpath/BazelClasspathContainerFTest.java"] + mock_src,
    deps = [
        ":com.salesforce.bazel.eclipse.core-bin",
        "//plugin-libs/plugin-model",
    ] + mock_deps,
    runtime_deps = mock_runtime_deps,
)

java_test(
    name = "BazelBuilderFTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/builder/BazelBuilderFTest.java"] + mock_src,
    deps = [
        ":com.salesforce.bazel.eclipse.core-bin",
        "//plugin-libs/plugin-model",
    ] + mock_deps,
    runtime_deps = mock_runtime_deps,
)

java_test(
    name = "MockTest",
    srcs = ["src/test/java/com/salesforce/bazel/eclipse/mock/test/MockTest.java"] + mock_src,
    deps = [
        ":com.salesforce.bazel.eclipse.core-bin",
        "//plugin-libs/plugin-model",
    ] + mock_deps,
    runtime_deps = mock_runtime_deps,
)
