## Understanding the Java Classpath

This page is common to both - the Java Lanaguage Service and the Bazel Eclipse Feature.
Therefore the term IDE will match to both.

The IDE ultimately computes classpath information and maps it into the existing Java Developer Tools (JDT) plug-in.
This works well, but there are some limitations.
In general Bazel is a very flexible build system.
IDEs are not that flexible.

The classpath is computed by a series of Bazel command.
These commands querying Bazel for jar information and also execute a Bazel build with aspects to generate jars with generated code and obtain additional information.
The aspects are re-used from the IntelliJ Bazel plugin.

The classpath is computed during Sync initially and can be updated separately from Sync.
Limited support exist for manipulating the `.classpath` files (eg., manually configuring classpath in Eclipse).
During refactorings or experiments this can become useful.
A Sync will typically restore the expected Bazel project setup and classpath.


### Basics: Classpath

In Eclipse each project has its own classpath.
Depending on the `target_provisioning_strategy` the project will either match the classpath of a single target or multiple.

Additionally, Eclipse supports two classpaths per project - one for regular code and one for tests.
Bazel tests targets and their dependencies will be mapped to the test classpath on a best effort base.
This can be fine tuned in the project view (`.bazelproject` file).

It is recommended to disable Bazel's default behavior making JUnit test dependencies available into the classpath.
Instead declare dependencies on JUnit & co explicitly.
The following `.bazelrc` entry is recommended.

```
# disable leaking of Bazel JUnit test dependencies into classpath
# (this confuses IDEs and developers, we have our own test frameworks and want only those to be used)
common --explicit_java_test_deps
```


**Project Dependencies**

Every dependency will be checked for a project first.
The dependencie's target label will be used to discover existing projects.
If a project is found it will be used and wired as a dependencies.

The discovery algorithm has limited supports for external workspace references.
In order for it to work the external workspace must also be imported into the IDE as Bazel workspace.

For more intentional mappings setup `project_mappings` in the [project view](projectviews.md).
It should allow to also connect other Java projects (eg. from Maven/M2E).

**Jar Dependencies**

Bazel uses header jars (or ijars) during compilation.
We try to avoid these jars in the IDE setup, i.e. we prefer the full jars.
This makes the implementation for running unit tests easier.

The IDE will also search for a matching `-src.jar` or `-sources.jar` to attach source code information to jars.
For packages and targets not included in the project view you need to build with additional options to ensure Bazel creates the source jars.
The following `.bazelrc` entry is recommended.

```
# For having sources available in the IDE always create source jars
# (Bazel doesn't do this by default)
build --output_groups=+_source_jars
```

External (third party) jars are supported.
Support exist for [rules_jvm_external](https://github.com/bazelbuild/rules_jvm_external) as well as `java_import` based solutions (eg.[jvm_import_external](https://github.com/bazelbuild/bazel/blob/master/tools/build_defs/repo/jvm.bzl)).


### Runtime Classpath

The runtime classpath is different from the compile classpath.
It is required when launching JUnit tests from within the IDE or attaching a debugger to a running Bazel binary.

The full transitive runtime classpath is obtained when running the build with aspects.
There is limited support to also resolve projects used from the runtime classpath.
Runtime classpath entries are added to the project but access rules are defined so compilation will produce an error if the jar is not a direct `deps` dependency.

The IDE may not be immediately usable for debugging while the Bazel command is still running.
Especially when source code is missing the IDE may attempt to repeat runtime classpath resolutions.
If you discover such a situation please don't hesitate and open an issue with a reproducible case.


### The Workspace Project

The classpath of the workspace project is a bit different.
The workspace project is not allowed to have Java sources, i.e. it will not have source folders.
However, it will have dependencies to all projects from packages and targets defined in the project view.

Additionally, if `discover_all_external_and_workspace_jars` is set it will also contain all external jars and jars from targets not listed in the project view.
This helps with refectorings because it allows the IDE to discover references to modified Java code within these jars.
When discovered the IDE will warn about incomplete refactorings, which can lead to a broken Bazel build.

Deploy jars and test projects (`java_binary` and `java_test` targets) are excluded from the list.
Our experience show that they contain lots of duplicated code and cannot be used as dependencies regularly.
