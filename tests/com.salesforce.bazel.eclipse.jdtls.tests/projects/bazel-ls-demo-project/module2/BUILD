load("@rules_java//java:defs.bzl", "java_library")

java_library (
    name = "module2",
    srcs = glob(["java/src/**/*.java"]),
    visibility = ["//module1:__pkg__"],
    deps = ["//module3"]
)

java_test(
    name = "module2-test",
    srcs = ["java/test/library/GreetingTest.java"],
    test_class = "library.GreetingTest",
    deps = [
        "module2",
        "@junit//jar",
    ],
)
