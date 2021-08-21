## Aspect Definition File

The file contained in this directory is used at runtime within the Bazel Java SDK.
It injects a [Bazel Aspect](https://docs.bazel.build/versions/master/skylark/aspects.html) into each Bazel build.

It is developed by the Bazel IntelliJ plugin team, and we copy it into Bazel SDK from time to time.

### Build Location?

It is tempting to move this to *src/main/resources* but these files need to remain extracted on the file system.
This likely means you need to install these files in addition to the java_binary that you build.

The class _BazelAspectLocationImpl_ depends on finding the *bzljavasdk_aspect.bzl* file on the file system.
