## Preferences and Settings with the Bazel Eclipse Feature

As a power user, you will want to configure your IDE the way you like it.
This page documents some ways to do that.

### Preferences

The Bazel Eclipse Feature does allow you to set some preferences.
This is a growing list, and so this documentation may not cover all of them.
Navigate to your Eclipse Preferences (menu option varies by platform) and click on the Bazel submenu.

- *Path to the Bazel binary*: tells BEF which Bazel binary to invoke for build commands
- *Enable global classpath search*: if true, allows BEF to index all jars used by the Bazel workspace such that they are visible when you do a Type Search.
- *Path to the local cache of downloaded jar files*: if global classpath search is enabled, allows you to override where BEF should look for the local cache of jar files. In some cases, this is difficult for BEF to determine.

### More Docs TODO

Topics:

- The output location for each source directory - needed to launch Java apps


### Next Topic: Builds and the Bazel Eclipse Feature

The next page in our guide discusses the [compilation and builds](using_the_feature_builds.md) with the Bazel Eclipse Feature.
