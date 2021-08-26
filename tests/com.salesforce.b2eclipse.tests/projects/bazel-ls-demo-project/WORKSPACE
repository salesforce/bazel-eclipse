workspace(name = "bazel_ls_demo_project")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# bazel-skylib 0.8.0 released 2019.03.20 (https://github.com/bazelbuild/bazel-skylib/releases/tag/0.8.0)
skylib_version = "0.8.0"
http_archive(
    name = "bazel_skylib",
   type = "tar.gz",
   url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib.{}.tar.gz".format (skylib_version, skylib_version),
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
)

# check minimum Bazel version
load("@bazel_skylib//lib:versions.bzl", "versions")
versions.check(minimum_bazel_version= "2.0.0")

http_archive(
    name = "salesforce_rules_mybuilder",
    url = "https://github.com/salesforce/bazel-java-builder-template/archive/ea683699c346a0b75960dd3bb02aa21fc910fdb4.zip",
    strip_prefix = "bazel-java-builder-template-ea683699c346a0b75960dd3bb02aa21fc910fdb4"
)
load("@salesforce_rules_mybuilder//mybuilder:repositories.bzl", "rules_mybuilder_dependencies", "rules_mybuilder_toolchains")
rules_mybuilder_dependencies()
rules_mybuilder_toolchains()

# Maven dependencies
load("//third_party/maven:dependencies.bzl", "maven_dependencies")
maven_dependencies()

