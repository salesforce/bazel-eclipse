#!/bin/bash
# A small target to run in Google Cloud Container Builder so the result is unzipped.

OUTPUT_DIR="${1:-bazel-updatesite}"
RUNFILES="${JAVA_RUNFILES:-$0.runfiles}"

unzip -d "${OUTPUT_DIR}" "${RUNFILES}/salesforce_bazel_eclipse/p2updatesite.zip"
