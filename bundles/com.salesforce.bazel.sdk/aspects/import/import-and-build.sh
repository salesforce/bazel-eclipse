#!/usr/bin/env bash
set -e

# clean-up old left overs
rm -rf bazel-*
rm -rf intellij/
rm -rf intellij_platform_sdk/

# ensure directory exists
mkdir -p intellij
mkdir -p intellij_platform_sdk

if [ "$(uname)" == "Darwin" ]; then
    tar="gtar"
else;
    tar="tar"
fi

if ! command -v $tar &> /dev/null
then
    echo "$tar could not be found"
    exit 1
fi

# download & extract
#
#   Note, wehn updating:
#     1. replace the hash with the one you want to update to
#     2. check WORKSPACE for any repo that needs updates
#
git_sha="37813e607ad26716c4d1ccf4bc3e7163b2188658"
git_sha_short=${git_sha::6}
curl -L https://github.com/bazelbuild/intellij/archive/${git_sha}.tar.gz | gtar --strip-components 1 -C intellij -xz
cp -r intellij/intellij_platform_sdk/* intellij_platform_sdk/

# generate tarball
bazel build :aspects

# copy to location
cp -vf bazel-bin/aspects.zip ../aspects-${git_sha_short}.zip
