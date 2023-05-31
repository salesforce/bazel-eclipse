## IntelliJ Aspects

The file contained in this directory is used at runtime within the Bazel Java SDK.
It injects a [Bazel Aspect](https://docs.bazel.build/versions/master/skylark/aspects.html) into each Bazel build.

It is developed by the Bazel IntelliJ plugin team, and we copy it into Bazel SDK from time to time.

### Updating from IntelliJ

The update is a bit automated.

1. Grab the Git SHA you want to update to from [here](https://github.com/bazelbuild/intellij/commits/master/aspect)
2. Update `import/import-and-build.sh` with the SHA.
3. Run `import/import-and-build.sh` (` cd import && ./import-and-build.sh`)
4. Update [IntelliJAspects.java](../src/main/java/com/salesforce/bazel/sdk/aspects/intellij/IntellijAspects.java) to point to the new `aspects-<sha>.zip` file

### Notes

If you get a build error about `no such package '@intellij_....//'` copy the relevant `http_archive` entry from the [IJ WORKSPACE file](https://github.com/bazelbuild/intellij/blob/master/WORKSPACE) and add it to `import/WORKSPACE`.


