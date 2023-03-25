load("@rules_pkg//pkg:mappings.bzl", "pkg_attributes", "pkg_filegroup", "pkg_files", "pkg_mkdirs", "strip_prefix")
load("@rules_pkg//pkg:zip.bzl", "pkg_zip")

pkg_files(
    name = "intelli_aspects",
    srcs = [
        "@intellij//aspect:aspect_files",
    ],
    # this magic is needed to get the jars inside the 'tools' directory
    strip_prefix = strip_prefix.from_pkg("//aspect"),
)

pkg_zip(
    name = "aspects",
    srcs = [
        ":intelli_aspects",
    ],
)
