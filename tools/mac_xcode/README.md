## Mac Xcode Support for Bazel

Generally, the Bazel build for the Bazel Eclipse Feature just works on Mac OS.
But there is one workaround that is necessary at times...

### Error: "Xcode version must be specified to use an Apple CROSSTOOL"

This happens sometimes on Macs when doing active development.
It is triggered when you refer to a non-existent dependency in a BUILD file.
Run the Xcode [fixer script](mac_xcode_fix.sh) if you see this.

This is a Bazel bug, and they are aware of it, see [Bazel Issue 4314](https://github.com/bazelbuild/bazel/issues/4314).
