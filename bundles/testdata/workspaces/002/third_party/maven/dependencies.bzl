load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

def maven_dependencies(
        maven_servers = ["https://repo1.maven.org/maven2/"]):

    jvm_maven_import_external(
        name = "com_google_guava",
        artifact = "com.google.guava:guava:28.2-jre",
        artifact_sha256 = "fc3aa363ad87223d1fbea584eee015a862150f6d34c71f24dc74088a635f08ef",
        fetch_sources = True,
        licenses = ["notice"],
        server_urls = maven_servers,
    )

    jvm_maven_import_external(
        name = "junit",
        artifact = "junit:junit:4.13",
        artifact_sha256 = "4b8532f63bdc0e0661507f947eb324a954d1dbac631ad19c8aa9a00feed1d863",
        fetch_sources = True,
        licenses = ["notice"],
        server_urls = maven_servers,
    )

    jvm_maven_import_external(
        name = "org_apache_commons_commons_lang3",
        artifact = "org.apache.commons:commons-lang3:jar:3.12.0",
        artifact_sha256 = "d919d904486c037f8d193412da0c92e22a9fa24230b9d67a57855c5c31c7e94e",
        fetch_sources = True,
        licenses = ["notice"],
        server_urls = maven_servers,
    )