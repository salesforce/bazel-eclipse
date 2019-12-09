## Releases of the Bazel Eclipse Feature

This page provides information on how to install a version of the Bazel Eclipse feature into your Eclipse install.

## Release Packaging

Eclipse features and plugins are packaged as an *update site*, which is either:
- a live HTTP site with the files
- a local .zip archive that contains the files

## Available Releases

We do not currently host an Eclipse Update Site for this feature, but we do offer pre-built binaries:

- [Bazel Eclipse Feature releases](https://github.com/salesforce/bazel-eclipse/releases)

## Build the Bazel Eclipse Feature yourself

These steps assume you have already setup your toolchain (JDK, Bazel) which is covered
  [here](install.md).

Note that the way to build the update site zip archive is an evolving story.
Read the documentation on the [build documentation page](dev/threebuilds.md) which will have instructions on how best to do it.
