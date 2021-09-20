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

    jvm_maven_import_external(
        name = "junit",
        artifact = "junit:junit:4.13",
        artifact_sha256 = "4b8532f63bdc0e0661507f947eb324a954d1dbac631ad19c8aa9a00feed1d863",
        licenses = ["notice"],
        server_urls = maven_servers,
    )
