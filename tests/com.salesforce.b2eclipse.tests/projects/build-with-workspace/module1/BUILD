load("@rules_java//java:defs.bzl", "java_binary")

java_binary(
    name = "ProjectRunner",
    srcs = glob(["src/main/java/com/example/ProjectRunner.java"]),
    main_class = "com.example.ProjectRunner",
    deps = [":greeter"],
)

java_library(
    name = "greeter",
    srcs = ["src/main/java/com/example/Greeting.java"],
)
