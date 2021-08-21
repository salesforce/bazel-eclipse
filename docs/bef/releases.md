## Releases of the Bazel Eclipse Feature ![BEF Logo](../logos/bef_logo_small.png)

This page provides information on how to install a version of the Bazel Eclipse feature into your Eclipse install.

## Release Packaging

Eclipse features and plugins are packaged as an *update site*, which is either:
- a live HTTP site with the files
- a local .zip archive that contains the files

## Available Releases

Our list of releases is here:

- [Bazel Eclipse Feature releases](https://github.com/salesforce/bazel-eclipse/releases)

We maintain our latest release on our public update site. The update site is available here:

- [Bazel Eclipse Update Site Instructions](https://opensource.salesforce.com/bazel-eclipse/)
<!-- markdown-link-check-disable-next-line -->
- Bazel Eclipse Update Site: https://opensource.salesforce.com/bazel-eclipse/update-site

If you prefer, we also attach a built binary archive of each release that you can install.
Find them in our [releases list](https://github.com/salesforce/bazel-eclipse/releases).

## Build the Bazel Eclipse Feature yourself

These steps assume you have already setup your toolchain (JDK, Bazel) which is covered
  [here](install.md).

Note that the way to build the update site zip archive is an evolving story.
Read the documentation on the [build documentation page](../dev/thebuild.md) which will have instructions on how best to do it.
