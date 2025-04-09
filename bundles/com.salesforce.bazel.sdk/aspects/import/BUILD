load("@aspect_bazel_lib//lib:run_binary.bzl", "run_binary")
load("@bazel_skylib//rules:write_file.bzl", "write_file")
load("@rules_pkg//pkg:mappings.bzl", "pkg_attributes", "pkg_filegroup", "pkg_files", "pkg_mkdirs", "strip_prefix")
load("@rules_pkg//pkg:zip.bzl", "pkg_zip")

write_file(
    name = "generate_extract_aspect_lib_jar_sh",
    out = "extract_aspect_lib_jar.sh",
    content = [
        "#!/usr/bin/env bash",
        "set -o errexit -o nounset -o pipefail",
        "$JAVABASE/bin/jar xf $2 || fail \"Failed to extract $2\"",
        "$JAVABASE/bin/jar xf $3 || fail \"Failed to extract $3\"",
        "mkdir -p $1",
        "mv aspect/* $1",
    ],
    is_executable = True,
)

sh_binary(
    name = "extract_aspect_lib_jar_binary",
    srcs = [":extract_aspect_lib_jar.sh"],
)

run_binary(
    name = "aspect_lib",
    srcs = [
        "aspect_lib.jar",
        "aspect_template_lib.jar",
        "@bazel_tools//tools/jdk:current_java_runtime",
    ],
    args = [
        "$(@D)",
        "$(location aspect_lib.jar)",
        "$(location aspect_template_lib.jar)",
    ],
    env = {
        "JAVABASE": "$(JAVABASE)",
    },
    out_dirs = ["aspect_lib"],
    progress_message = "Extracting aspect_lib.jar and aspect_template_lib.jar",
    tool = ":extract_aspect_lib_jar_binary",
    toolchains = [
        "@bazel_tools//tools/jdk:current_java_runtime",
    ],
)

pkg_files(
    name = "intelli_aspects",
    srcs = [
        ":aspect_lib",
    ],
    strip_prefix = "aspect_lib/",
)

pkg_zip(
    name = "aspects",
    srcs = [
        ":intelli_aspects",
    ],
)
