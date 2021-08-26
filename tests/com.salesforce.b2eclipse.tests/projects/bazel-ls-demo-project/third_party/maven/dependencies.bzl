load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

def maven_dependencies(
        maven_servers = ["https://repo1.maven.org/maven2/"]):

    jvm_maven_import_external(
        name = "com_google_guava",
        artifact = "com.google.guava:guava:28.2-jre",
        artifact_sha256 = "fc3aa363ad87223d1fbea584eee015a862150f6d34c71f24dc74088a635f08ef",
        licenses = ["notice"],
        server_urls = maven_servers,
    )

