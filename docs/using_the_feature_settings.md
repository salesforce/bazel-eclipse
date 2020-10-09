## Preferences and Settings with the Bazel Eclipse Feature

As a power user, you will want to configure your IDE the way you like it.
This page documents some ways to do that.

### Bazel Preferences

The Bazel Eclipse Feature does allow you to set some preferences.
Navigate to your Eclipse Preferences (menu option varies by platform) and click on the Bazel submenu.
This is a growing list, and each should be documented in the topic specific pages.
As a new BEF user, the default values should be fine.

There is one key preference that you may need to adjust:

- *Path to the Bazel binary*: tells BEF which Bazel binary to invoke for build commands

### User Default Preferences

If you are a power user of BEF, and create many workspaces, you may wish to save your common defaults to a global file.
This will save time when creating a new BEF workspace.

File location: **~/.bazel/eclipse.properties**

```
# always default Bazel executable to be bazelisk in my non-standard location
BAZEL_PATH=/Users/mbenioff/dev/tools/bazelisk-darwin-amd64
```

Instead of documenting the available properties here (which would get outdated),
  please search for the *BazelPreferenceKeys.java* class in the code base.


### Next Topic: Builds and the Bazel Eclipse Feature

The next page in our guide discusses the [compilation and builds](using_the_feature_builds.md) with the Bazel Eclipse Feature.
