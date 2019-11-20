### Plugin Dependencies

Why are there jars checked into the Git repository instead of using Bazel maven_jar rules?
Because we support [two different build systems in parallel](../../docs/dev/threebuilds.md)
  and having the jar files here makes it easiest to ensure both systems use the same deps.

This directory is for *main* dependencies.
There is a separate directory for [test dependencies](../plugin-testdeps)

If you want to add/change a jar file, you will need to:

- **Bazel Build:** open the [BUILD](BUILD) file here and add/change it. There are examples in the file, so it should be obvious what to do.
- **Eclipse SDK Build:** open the [.classpath](.classpath) file here and add/change it. There are examples in the file, so it should be obvious what to do.

If you just added a jar file, you will now need to reference it from the projects that need it.
Grep around the plugin project to find examples.
Be sure to add it for both builds (Bazel, Eclipse SDK) by touching *BUILD* and *MANIFEST.MF* files.
