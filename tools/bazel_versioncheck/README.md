## Bazel Version Check

This is a static copy of the Bazel [Skylib version check utility](https://github.com/bazelbuild/bazel-skylib/blob/master/lib/versions.bzl).
It checks to make sure the version of Bazel running the build is recent enough.
The version check itself is invoked from the [WORKSPACE](../../WORKSPACE) file.

It was copied into this project (i.e. vendor'd) because it is a small library and we don't need to stay
  on the latest version.
The alternate is to use a remote repository, but that would require CI builds to reach out to the internet.
