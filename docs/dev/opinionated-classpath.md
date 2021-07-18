# Opinionated Java Classpath Container

This document describes a proposal for a new way of calculating the Java classpath in Eclipse for Bazel projects.

## Problem Statement

Bazel dependencies for Java targets are listed in `BUILD.bazel` files (build files).
The classpath in Eclipse is computed from these entries in build files.

The dependencies need to be specified very precise, i.e. Bazel does not allow transitive dependencies.

When working with third-party libraries (eg., Google Guava) it's a trivial task to modify the build files and add necessary entries.

In order for any Bazel project to excel at incremental compilation, fine grained packages are recommended, i.e. Bazel packages should be defined for individual Java packages.
When working with such fine grained Bazel packages within a large project it become a painful and diligent exercise.

Multiple questions need to be answered before the build file can be updated:
- *Where is the dependency coming from?*
- *What is the Bazel target name of a third-party library?*
- *Which libraries are available for the package?*
- *Is the target visible to the package?*


## Proposed Solution

The opinionated classpath container addresses the problem by reversing it.
Instead of reading a Bazel Java package's dependencies from the build file the build file will be populated from what the Bazel package actually uses.
When a Bazel package's code is compiled in Eclipse all imports will be analyzed and traced back to the origin.
The result will be collected and written to the dependency attributes of the Java target after compilation.

To prevent a classpath from becoming too large for Bazel packages we need to allow implement visibility rules.
Developers need to be able to specify what any Bazel package is *allowed to import*.

Part of this may come from Bazel visibility attributes applied to existing targets.

In addition to those defined in Bazel targets the container supports rules defined by [Trellis](https://github.com/salesforce/trellis).
Trellis rules MUST take priority of Bazel visibility attributes.
If a conflicting Bazel visibility attribute is found it will be updated to the visibility matching Trellis.

## Non Goals

Runtime dependencies will not be managed by this container.
