## Bazel Eclipse Feature: Core Plugin

In the [architecture](../docs/dev/architecture.md) of the Bazel Eclipse Feature, the implementation is spread across a few Eclipse plugins.

- **plugin-core**: this plugin is the one that is integrated with Eclipse APIs, and contains classes such as the activator
- **bazel-java-sdk**: handles model abstractions and command execution for Bazel

This package contains the core plugin.

### Eclipse API Integration

The core plugin (plugin-core) is the heart of the feature.
It contains the integration with the actual Eclipse APIs.

It provides these facilities:

- Activator for the feature
- Build hook that gets called when source code changes
- Bazel Classpath container which provides the classpath to Eclipse for each project
- Workspace import wizard

and many more features.
