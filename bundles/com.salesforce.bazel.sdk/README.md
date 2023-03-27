# Bazel Java SDK and IntelliJ Helpers

This directory contains the Bazel Java SDK, which is a general purpose Java library for building tools on top of Bazel in the Java programming language.
It implements a number of Bazel helpers and tools, including:

- Bazel commands and and executors to run Bazel processes and process outputs
- Protobuf `proto` files and generated source
- IntelliJ Aspects for extracting IDE information from Bazel and related Java code
- Bazel primitives (also extracted from IntelliJ Bazel plug-in)

The SDK will is a helpful library.
It's not intended to be an abstraction for writing IDE agnostic models/code.
At some point we should discuss with the Bazel IntelliJ folks if a common library can be extracted from their plug-in to be reused by BEF as well.
