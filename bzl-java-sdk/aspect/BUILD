# This is a build file for creating a corresponding package. This package will be a local repository
# added with --override_repository when using the aspect so we can load the bzljavasdk_aspect.bzl Skylark
# aspect extension when calling build from the Bazel Java SDK.

filegroup(
   name = "srcs",
   srcs = glob(["**"]),
   visibility = ["//visibility:public"],
)
