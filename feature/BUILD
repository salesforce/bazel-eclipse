load(
    "//tools/eclipse:eclipse_feature.bzl",
    "eclipse_feature",
)
load(
    "//tools/eclipse_updatesite:eclipse_updatesite.bzl",
    "eclipse_p2updatesite",
)
load("//tools:feature_version_def.bzl", "VERSION")

# Build the Eclipse Feature metadata files and jar
# In practice, we actually aren't using these outputs at all.
# We have found that the Eclipse SDK does a better job at it.
# See this package's README for more information
eclipse_feature(
    name = "com.salesforce.bazel.eclipse.feature",
    copyright = "Copyright 2016 The Bazel Authors, 2018 Salesforce",
    description = "Integrate Eclipse with the Bazel build system.",
    label = "Bazel Eclipse Feature",
    license = "//:LICENSE.txt",
    license_url = "http://www.apache.org/licenses/LICENSE-2.0",
    plugins = [
      "//plugin-core:com.salesforce.bazel.eclipse.core",
      "//plugin-libs/plugin-abstractions:com.salesforce.bazel.eclipse.abstractions",
      "//plugin-libs/plugin-command:com.salesforce.bazel.eclipse.command",
      "//plugin-libs/plugin-model:com.salesforce.bazel.eclipse.model",
      "//plugin-libs/plugin-deps:com.salesforce.bazel.eclipse.deps",
    ],
    provider = "Salesforce, Bazel Authors",
    sites = {"Bazel Plugin": "https://git.soma.salesforce.com/services/bazel-eclipse"},
    url = "https://git.soma.salesforce.com/services/bazel-eclipse",
    version = VERSION,
    visibility = ["//visibility:public"],
)


# We are not using Bazel to build the feature updatesite any more, and likely never will.
# See this package's README for an explanation.

#eclipse_p2updatesite(
#    name = "bazeleclipsefeature",
#    description = "Bazel Eclipse Feature",
#    eclipse_features = [":com.salesforce.bazel.eclipse.feature"],
#    label = "Bazel Eclipse Feature",
#    url = "https://git.soma.salesforce.com/services/bazel-eclipse",
#    visibility = ["//tools/eclipse_updatesite:__pkg__"],
#)
