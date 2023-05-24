load("@rules_java//java:defs.bzl", "java_binary")
load("@salesforce_rules_mybuilder//mybuilder:defs.bzl", "mybuilder_gen_java_library")

filegroup(
    name = "mybuilder_input",
    srcs = glob(
        [
            "java/resources/**/*.txt",
        ],
    ),
)

filegroup(
    name = "module1_sources",
    srcs = glob(
        [
            "java/src/**/*.java",
        ],
    ),
)

mybuilder_gen_java_library(
    name = "mybuilder_sources",
    srcs = [":mybuilder_input"],
)


java_binary(
    name = "module1",
    srcs = [
        "module1_sources",
    ],
    resources = [":mybuilder_input"],
    deps = [
      "//module2",
      "//module3",
      "@com_google_guava//jar",
      "mybuilder_sources",
    ],
    main_class = "hello.Hello",
)
