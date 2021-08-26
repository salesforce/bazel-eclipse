load("@rules_java//java:defs.bzl", "java_binary")
load("@salesforce_rules_mybuilder//mybuilder:defs.bzl", "mybuilder_gen")

mybuilder_gen(
    name = "mybuilder_sample_generation",
    srcs = glob(
        ["java/resources/**/*.txt"],
    ),
    srcjar = "mybuilder_generated.srcjar",
    gendir = "mybuilder_generated",
)

java_binary(
    name = "module1",
    srcs = glob(["java/src/**/*.java"]) + [":mybuilder_sample_generation"],
    resources = glob(
        ["java/resources/**/*.txt"],
    ),
    deps = [
      "//module2",
      "//module3",
      "@com_google_guava//jar",
    ],
    main_class = "hello.Hello",
)
