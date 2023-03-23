# Proto File

This directory contains `.proto` files used in the Java SDK as well as the Eclipse plug-ins.
When they change in their original repository, the need to be updated here.

For convenience the generated Java code is checked into source control.
Generators have been provided for Eclipse plugged into the *Run > External Tools* menu as shortcuts.

* `intellij/intellij_ide_info.proto` [13287a2](https://github.com/bazelbuild/intellij/commits/37813e607ad26716c4d1ccf4bc3e7163b2188658/proto/intellij_ide_info.proto)
* `intellij/common.proto` [46582ba](https://github.com/bazelbuild/intellij/commits/37813e607ad26716c4d1ccf4bc3e7163b2188658/proto/common.proto)
* `bazel/build_event_stream.proto` and dependencies [be7458a](https://github.com/bazelbuild/bazel/blob/be7458ad7c96b590e9fdec4c3022b60bf8aa9d05/src/main/java/com/google/devtools/build/lib/buildeventstream/proto/build_event_stream.proto)


## Full Vendoring

Note, for a easier build experience all dependencies have been included here.
This requires updating `import` statements in the `proto` files when versioning them here.
