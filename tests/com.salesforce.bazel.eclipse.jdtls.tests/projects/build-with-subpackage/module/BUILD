load("@rules_java//java:defs.bzl", "java_binary")

java_binary(
    name = "ProjectRunner",
    srcs = glob(["java/src/example/ProjectRunner.java"]),
    main_class = "example.ProjectRunner",
    deps = ["//module/submodule:greeter"],
)
