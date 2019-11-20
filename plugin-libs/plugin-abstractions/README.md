## Bazel Eclipse Feature: Abstractions Plugin

In the [architecture](../docs/dev/architecture.md) of the Bazel Eclipse Feature, the implementation is spread across 4 Eclipse plugins.

- **plugin-core**: this plugin is the one that is integrated with Eclipse APIs, and contains classes such as the activator
- **plugin-model**: model objects for the various concepts within the feature
- **plugin-abstractions**: has plain interfaces to stand in the place of Eclipse APIs
- **plugin-command**: provides the Bazel command line integration

### Plain Interfaces

This package exists to provide plain Java interfaces for Eclipse APIs.
It allows most plugin code to use these abstractions and not depend on Eclipse classes.
This makes testing easier.
