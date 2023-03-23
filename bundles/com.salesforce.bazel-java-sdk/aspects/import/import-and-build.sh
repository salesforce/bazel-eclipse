#!/usr/bin/env bash
set -e

# clean-up old left overs
rm -rf bazel-*
rm -rf intellij/

# ensure directory exists
mkdir -p intellij

# download & extract
#  
#   Note, wehn updating:
#     1. replace the hash with the one you want to update to
#     2. check WORKSPACE for any repo that needs updates
#
git_sha="37813e607ad26716c4d1ccf4bc3e7163b2188658"
git_sha_short=${git_sha::6}
curl -L https://github.com/bazelbuild/intellij/archive/${git_sha}.tar.gz | gtar --strip-components 1 -C intellij -xvz

# generate tarball
bazel build :aspects

# copy to location
cp -vf bazel-bin/aspects.zip ../aspects-${git_sha_short}.zip
