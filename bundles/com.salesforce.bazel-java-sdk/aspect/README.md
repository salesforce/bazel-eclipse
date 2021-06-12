## Aspect Definition File

The file contained in this directory is used at runtime within the Bazel Java SDK.
It injects a [Bazel Aspect](https://docs.bazel.build/versions/master/skylark/aspects.html) into each Bazel build.

### Build Location?

It is tempting to move this to *src/main/resources* but these files need to remain extracted on the file system.
This likely means you need to install these files in addition to the java_binary that you build.

The [BazelAspectLocationImpl](../src/main/java/com/salesforce/bazel/eclipse/config/BazelAspectLocationImpl.java#L96)
  depends on finding the *bzljavasdk_aspect.bzl* file on the file system.
