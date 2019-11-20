## Bazel Eclipse Feature: Model Plugin

In the [architecture](../docs/dev/architecture.md) of the Bazel Eclipse Feature, the implementation is spread across 4 Eclipse plugins.

- **plugin-core**: this plugin is the one that is integrated with Eclipse APIs, and contains classes such as the activator
- **plugin-model**: model objects for the various concepts within the feature
- **plugin-abstractions**: has plain interfaces to stand in the place of Eclipse APIs
- **plugin-command**: provides the Bazel command line integration

### Models

This package contains the simple Java model objects for important concepts in the Bazel Eclipse Feature
