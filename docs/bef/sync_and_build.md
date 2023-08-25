## Updating and Building your Workspace ![BEF Logo](../logos/bef_logo_small.png)

This page contains information related to workspace builds using the feature.

### Basics: Sync

Please have a look at [Sync Basics](../common/sync.md) to understand what *sync* is and *when* it's needed.


### How to Sync?

A sync can take very long time.
Therefore BEF will never sync automatically.
In order to sync manually use one of the following options.

1. *Right click* on any Bazel project in *Package Explorer* or *Project Explorer*
2. Select *Bazel > Sync Bazel Project View*

**or**

1. Select any Bazel project in *Package Explorer* or *Project Explorer*
2. Use *Cmd/Ctrl+3* to open the command palett and search for *Sync Bazel Project View*

**or**

1. Find and *click* the *Sync Bazel Project Views* button in the Eclipse main toolbar

Note, the toolbar button will synchronize **all** Bazel workspaces and their project views (in case you have multiple imported in Eclipse).


### Basics: Build

Eclipse support auto building.
In this mode, Eclipse will re-compile modified Java source files upon save.
This generates the class file and will be used for HotSwap support.
However, the jar is not updated (yet).
A Bazel build is required to update the jar.

For more information on how the feature invokes Bazel, see the [architecture page](../dev/architecture.md).


### Next Topic: Understanding the Java Classpath

The next page in our guide discusses the [Java classpath](../common/classpath.md).
