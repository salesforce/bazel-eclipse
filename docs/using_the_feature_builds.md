## Building your Workspace with the Bazel Eclipse Feature

This page contains information related to workspace builds using the feature.

### Build Basics

The Bazel Eclipse Feature is notified whenever the user saves a file in the project.
The feature then invokes a command line build using the installed Bazel binary.
The build may succeed or fail.
Issues found during the build will be populated as markers in the *Problems* view.

Because the feature executes the Bazel binary as a user would from the command line,
  the build has high fidelity to the command line configuration.
For example, your *.bazelrc* file is honored by the feature.

For more information on how the feature invokes Bazel, see the [architecture page](dev/architecture.md).

### Next Topic: Launching binaries and tests from Eclipse

The next page in our guide discusses the [Launchers](using_the_feature_launching.md) with the Bazel Eclipse Feature.
