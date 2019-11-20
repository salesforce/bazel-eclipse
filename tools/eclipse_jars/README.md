## Eclipse Version and Jars for the Build

This directory contains rules for linking in the right Eclipse jars into the build.
To make this work, the [eclipse_jars.bzl script](eclipse_jars.bzl) contains the [Eclipse SDK version](eclipse_jars.bzl#L24) to use and
 the pinned version ids of [each Eclipse SDK jar](eclipse_jars.bzl#L32).
The Eclipse SDK jars are extracted from an Eclipse install and checked into the Git repo.

If you want to upgrade the SDK version that we use to build against, you will need to manually
  update all the versions in the [eclipse_jars.bzl script](eclipse_jars.bzl).
Yes, this is as painful as it sounds.
