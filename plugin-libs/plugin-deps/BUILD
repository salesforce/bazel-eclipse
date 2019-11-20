load(
    "//tools/eclipse:eclipse_plugin.bzl",
    "eclipse_plugin",
)
load("//tools:feature_version_def.bzl", "VERSION")


java_import(
    name = "com_google_guava",
    jars = [
        "lib/guava-21.0.jar",
    ],
    visibility = [
        "//visibility:public",
    ],
)

java_import(
    name = "org_json_json",
    jars = [
        "lib/json-20160212.jar",
    ],
    visibility = [
        "//visibility:public",
    ],
)

# SLF4J-API is a weird one. It comes along for free in the Eclipse SDK
java_import(
    name = "org_slf4j_slf4j_api",
    jars = [
        "lib/slf4j-api-1.7.7.jar",
    ],
    visibility = [
        "//visibility:public",
    ],
)

java_import(
    name = "ch_qos_logback_logback_core",
    jars = [
        "lib/logback-core-1.0.7.jar",
    ],
    visibility = [
        "//visibility:public",
    ],
)

java_import(
    name = "ch_qos_logback_logback_classic",
    jars = [
        "lib/logback-classic-1.0.7.jar",
    ],
    visibility = [
        "//visibility:public",
    ],
)

eclipse_plugin(
    name = "com.salesforce.bazel.eclipse.deps",
    srcs = glob(["src/main/java/**/*.java"]),
    bundle_name = "Bazel Eclipse Dependencies Plugin",
    vendor = "Salesforce, Bazel Authors",
    version = VERSION,
    exports = "org.json,org.slf4j,com.google.common.base,com.google.common.collect",
    visibility = ["//visibility:public"],
    deps = [
      ":com_google_guava",
      ":org_json_json",
      ":org_slf4j_slf4j_api",
    ],
)
