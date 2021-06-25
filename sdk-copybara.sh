#!/bin/bash

# Bazel Java SDK Vendoring Script

# This script does not actually use copybara, but it is named as such because
# most people will understand the use case. This is a sample script for
# vendoring the SDK into Bazel Eclipse using simple file copy primitives.

# SOURCE
if [ -z "${SDK_DIR+xxx}" ]; then
  echo "Please set the location of Bazel Java SDK on your filesystem using the SDK_DIR env variable."
  echo " This is the absolute path of the directory that contains the WORKSPACE file of bazel-java-sdk"
  exit 1
fi
sdk_root=$SDK_DIR


# DESTINATION
if [ ! -f LICENSE.txt ]; then
  echo "This script must be run from the root of the Bazel Eclipse repository."
  exit 1
fi
bef_root=$(pwd)

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
cp $sdk_root/sdk/bazel-java-sdk/aspect/* $bef_root/bundles/com.salesforce.bazel-java-sdk/aspect

# SDK Java Classes
cp -R $sdk_root/sdk/bazel-java-sdk/src/main/java/* $bef_root/bundles/com.salesforce.bazel-java-sdk/src/main/java

# Tests for SDK Java Classes
cp -R $sdk_root/sdk/bazel-java-sdk/src/test/java/* $bef_root/tests/com.salesforce.bazel-java-sdk.tests/src

# Test framework that emulates Bazel commands and creates test workspaces
cp -R $sdk_root/sdk/bazel-java-sdk-test-framework/src/main/java/* $bef_root/bundles/com.salesforce.bazel-java-sdk.testframework/src
