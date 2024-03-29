#!/usr/bin/env bash
set -e

# clean-up old left overs
rm -rf bazel-*
rm -rf intellij/
rm -rf intellij_platform_sdk/

# ensure directory exists
mkdir -p intellij
mkdir -p intellij_platform_sdk

# use proper tar
if [ "$(uname)" == "Darwin" ]; then
    tar="gtar"
else
    tar="tar"
fi
echo "Using '$tar' on $(uname)!"

if ! command -v $tar &> /dev/null; then
    echo "$tar could not be found"
    echo "On macOS: brew install gnu-tar"
    echo "Also, check PATH environment: $PATH"
    exit 1
fi

# download & extract
#
#   Note, wehn updating:
#     1. replace the hash with the one you want to update to
#     2. check WORKSPACE for any repo that needs updates
#
git_sha="76ff4072e0396b1904b819c957fd7aa43199e2b0"
git_sha_short=${git_sha::6}

# abort if file already exists
if test -f "../aspects-${git_sha_short}.zip"; then
    echo "aspects-${git_sha_short}.zip already there; delete it force re-import"
    exit 0
fi

# download repo
curl -L https://github.com/bazelbuild/intellij/archive/${git_sha}.tar.gz | ${tar} --strip-components 1 -C intellij -xz
cp -r intellij/intellij_platform_sdk/* intellij_platform_sdk/

# generate tarball
bazel build :aspects

# copy to location
cp -vf bazel-bin/aspects.zip ../aspects-${git_sha_short}.zip

