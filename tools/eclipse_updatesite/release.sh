#!/bin/bash

# This script performs the Git functions (tags, rel commit) related to a release of the plugin.
# It does not do anything to actually build the product.


MASTER_BRANCH=master
GIT_REPO='git@git.soma.salesforce.com:services/bazel-eclipse'

ROOTDIR="$(dirname $(dirname "$(cd "$(dirname "$0")" && pwd -P)"))"
cd "$ROOTDIR"
RELEASE_ORIGIN=${RELEASE_ORIGIN:-"$GIT_REPO"}

source tools/plugin_version.bzl
# Remove qualifier from VERSION if present
VERSION="${VERSION//\.qualifier}"

make_version_bzl() {
  cat <<EOF >tools/plugin_version.bzl
# Note: do not edit, use tools/release/release.sh script instead.
# This file is both a shell script and a skylark file.
VERSION="$1"
EOF
}

# Create the release tag
make_release_tag() {
  # Detach head
  git checkout -q --detach
  # Update the plugin_version.bzl file
  make_version_bzl "${VERSION}"
  # Create the commit
  git commit -q -m "Release ${VERSION}" tools/plugin_version.bzl
  # Create the tag
  git tag "${VERSION}"
  # Checkout back master
  git checkout -q $MASTER_BRANCH
}

# Create a commit for increasing the version number
make_release_commit() {
  git checkout -q $MASTER_BRANCH
  make_version_bzl "${1}.qualifier"
  git commit -q -m "Update version to ${1}.qualifer after release ${2}" \
    tools/feature_version_def.bzl
}

# Read the version number from the input
read_version() {
  while true; do
    echo -n "$1 [$VERSION] "
    read ans
    if [ -n "$ans" ]; then
      if [[ "$ans" =~ [0-9]+(\.[0-9]+)* ]]; then
        VERSION="$ans"
        return
      else
        echo "Please enter a version number (e.g. 0.3.0)." >&2
      fi
    else
      return
    fi
  done
}

# Produces all possible new versions
incr_version() {
  local v=(${1//./ })
  for (( i=${#v[@]}-1; $i >= 0; i=$i-1 )); do
    local new_v=""
    for (( j=0; $j < ${#v[@]}; j=$j+1 )); do
      local vj=${v[$j]}
      if (( $j == $i )); then
        vj=$(( ${v[$j]} + 1))
      fi
      if [ -n "${new_v}" ]; then
        new_v="${new_v}.${vj}"
      else
        new_v="${vj}"
      fi
    done
    echo "${new_v}"
  done
}

# Push the master branch and the given tag
push_master_and_tag() {
  git push "${RELEASE_ORIGIN}" $MASTER_BRANCH
  git push "${RELEASE_ORIGIN}" "$1"
}

# Do the release itself, the update site is build on GCCB
release() {
  read_version "About to release, which version?"
  make_release_tag
  local old_version="${VERSION}"
  local new_versions=($(incr_version "${VERSION}"))
  VERSION=${new_versions[0]}
  echo "Possible versions for next releases: ${new_versions[@]}"
  read_version "Next version will be?"
  make_release_commit "${VERSION}" "${old_version}"
  push_master_and_tag "${old_version}"
}

release
