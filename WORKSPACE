workspace(name = "bazel_eclipse_feature")


# ---------------------------------------------
# Bazel version check
# Ensure people are running with a good-enough version of Bazel
# ---------------------------------------------

load("//tools/bazel_versioncheck:versioncheck.bzl", "versions")
versions.check("1.0.0")


# ---------------------------------------------
# Eclipse deps
# ---------------------------------------------

load("//tools/eclipse_jars:eclipse_jars.bzl", "load_eclipse_deps")
load_eclipse_deps()


# ---------------------------------------------
# Maven/Nexus Deps
# ---------------------------------------------

# Do not add any Maven dependencies here (maven_jar, maven_install, jvm_maven_import_external).

# Most deps are managed in the //plugin-libs/plugin-deps and //plugin-libs/plugin-testdeps
# projects. They are managed there so that both build systems (Bazel, Eclipse SDK) use the
# same dependencies. However, there are some dependencies that are managed differently for
# the two parallel build systems. The Eclipse APIs are brought in via the //tools/build_defs
# package in Bazel, and via the plugin.xml facility when built from the Eclipse SDK.

#maven_jar(
#    name = "org_slf4j_slf4j_api",
#    artifact = "org.slf4j:slf4j-api:jar:1.6.2",
#)
