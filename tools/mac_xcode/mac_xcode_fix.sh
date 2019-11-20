# Manual fixer script for error when building on Mac: "Xcode version must be specified..."
# This can happen if you put a non-existent dep into the dependency list of a rule.
# This is being tracked in the Bazel Git repo:
#  https://github.com/bazelbuild/bazel/issues/4314
#  https://github.com/bazelbuild/bazel/issues/6056

echo "This script solves the problem on Mac when Bazel complains about 'Xcode version must be specified to use an Apple CROSSTOOL'"
echo "Enter your sudo password if prompted."

bazel clean --expunge
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -license
