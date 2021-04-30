## Aspect Definition File

The file contained in this directory is used at runtime within the Bazel Eclipse Feature.
It injects Aspects into each Bazel build.

### Not a Bazel Eclipse Feature build time resource
This WORKSPACE is NOT used in the running of the Bazel build for the feature.
It is copied into the Bazel Eclipse Feature jar as-is, and it is used when running Bazel command line
under the covers when using the Bazel feature.

### Build Location?

It is tempting to move this to *src/main/resources* but some machinery inside the feature creation
  would have to change.

The [BazelAspectLocationImpl](../src/main/java/com/salesforce/bazel/eclipse/config/BazelAspectLocationImpl.java#L96)
  depends on the *bzleclipse_aspect.bzl* file to be in the top-level *resources* directory in the plugin-core jar.
If you move this directory, you have to also change how the jar is packaged or that search code.
