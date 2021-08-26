load("@rules_java//java:defs.bzl", "java_library")

java_library (
    name = "module2",
    srcs = glob(["java/src/**/*.java"]),
    visibility = ["//module1:__pkg__"],
    deps = [
      "//module3"
    ]
)
