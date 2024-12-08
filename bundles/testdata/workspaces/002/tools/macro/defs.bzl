load("@rules_java//java:defs.bzl", "java_library", "java_test")

def my_macro(name, **kwargs):

    java_library (
        name = name,
        srcs = native.glob(["java/src/**/*.java"]),
        **kwargs
    )
