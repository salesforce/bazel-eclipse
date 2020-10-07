## Using the Bazel Eclipse Feature: Preferences

Once you have your initial Eclipse workspace up and running, you may want to set some preferences.

### Workspace Preferences

BEF supports a pane in the Eclipse preferences editor.
The way to open the preferences editor varies by platform.
On Mac, open it by navigating to *Eclipse -> Preferences -> Bazel*.

What each preference controls is documented in topic specific locations of our documentation.

### Global Preferences

If you are a power user of BEF, and create many workspaces, you may wish to save common defaults to a global file.
This will save time when creating a new BEF workspace.

File location: **~/.bazel/eclipse.properties**

```
# always default Bazel executable to be bazelisk in my non-standard location
BAZEL_PATH=/Users/mbenioff/dev/tools/bazelisk-darwin-amd64
```

Instead of documenting the available properties here (which would get outdated),
  please search for the *BazelPreferenceKeys.java* class in the code base.
