#!/bin/bash

# Bazel Java SDK Contribution Script

# This script copies the latest versions of the vendored bazel-java-sdk
# back into the bazel-java-sdk repository. This is done after making improvements
# to the SDK while developing Bazel Eclipse.

# This script does not actually use copybara, but it is named as such because
# most people will understand the use case.

# SOURCE
if [ ! -f LICENSE.txt ]; then
  echo "This script must be run from the root of the Bazel Eclipse repository."
  exit 1
fi
bef_root=$(pwd)

# DESTINATION
if [ -z "${SDK_DIR+xxx}" ]; then
  echo "Please set the location of Bazel Eclipse on your filesystem using the SDK_DIR env variable."
  echo " This is the absolute path of the directory that contains the WORKSPACE file of bazel-java-sdk"
  exit 1
fi
sdk_root=$SDK_DIR


# SDK
# sdk
#  bazel-java-sdk
#    aspect/*
#    src/main/java/* (aspect, command, index, lang, logging, model, project, workspace)
#    src/test/java/**/*Test.java
#  bazel-java-sdk-test-framework
#    src/main/java/**/test/*.java (mocks)

# BEF
# bundles
#    com.salesforce.bazel-java-sdk
#       aspect/*
#       src/main/java/* (aspect, command, index, lang, logging, model, project, workspace)
#    com.salesforce.bazel-java-sdk.testframework
#      src/main/java/**/test/* (mocks)
# tests
#   com.salesforce.bazel-java-sdk.tests
#    src/**/*Test.java

# Mapping Rules

# Aspect
cp -R $bef_root/bundles/com.salesforce.bazel-java-sdk/aspect/* $sdk_root/sdk/bazel-java-sdk/aspect

# SDK Java Classes
cp -R $bef_root/bundles/com.salesforce.bazel-java-sdk/src/main/java/* $sdk_root/sdk/bazel-java-sdk/src/main/java

# Tests for SDK Java Classes
cp -R $bef_root/tests/com.salesforce.bazel-java-sdk.tests/src/* $sdk_root/sdk/bazel-java-sdk/src/test/java

# Test framework that emulates Bazel commands and creates test workspaces
cp -R $bef_root/bundles/com.salesforce.bazel-java-sdk.testframework/src/* $sdk_root/sdk/bazel-java-sdk-test-framework/src/main/java
