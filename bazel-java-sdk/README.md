## Bazel Java SDK

In the [architecture](../docs/dev/architecture.md) of the Bazel Eclipse Feature, the implementation is spread across a few Eclipse plugins.

- **plugin-core**: this plugin is the one that is integrated with Eclipse APIs, and contains classes such as the activator
- **bazel-java-sdk**: handles model abstractions and command execution for Bazel

This package contains the SDK.
