## Bazel Eclipse Feature: Command Plugin

In the [architecture](../docs/dev/architecture.md) of the Bazel Eclipse Feature, the implementation is spread across 4 Eclipse plugins.

- **plugin-core**: this plugin is the one that is integrated with Eclipse APIs, and contains classes such as the activator
- **plugin-model**: model objects for the various concepts within the feature
- **plugin-abstractions**: has plain interfaces to stand in the place of Eclipse APIs
- **plugin-command**: provides the Bazel command line integration

### Bazel Command Line

This package contains the code necessary to invoke Bazel commands on the Bazel workspace.
As covered in the architecture document, when source files change in Eclipse, a command line build is invoked using Bazel.
Any errors are parsed from the output and added to the Eclipse Problems view.

This package contains all the code to do this.
