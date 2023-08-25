## Understanding the Java Classpath

This page is common to both - the Java Lanaguage Service and the Bazel Eclipse Feature.
Therefore the term IDE will match to both.

The IDE ultimately computes classpath information and maps it into the existing Java Developer Tools (JDT) plug-in.
This works well, but there are some limitations.
In general Bazel is a very flexible build system.
IDEs are not that flexible.


### Basics: Classpath

In Eclipse each project has its own classpath.
Depending on the `target_provisioning_strategy` the project will either match the classpath of a single target or multiple.

Additionally, Eclipse supports two classpaths per project - one for main and one for tests.
Bazel tests targets and their dependencies will be mapped to the test classpath on a best effort base.

** Project dependencies **

Every dependencies will be checked for a project first.
The dependencie's target label will be used to discover existing projects.
If a project is found it will be used and wired as a dependencies.

The discovery algorithm has limited supports for external workspace references.
In order for it to work the external workspace must also be imported into the IDE as Bazel workspace.

For more intentional mappings setup `project_mappings` in the [project view](projectviews.md).

** Jar Dependencies **

Bazel uses header jars (or ijars) during compilation.
The classpath in the IDE will be setup for compilation using those jars if available.
Otherwise the full jar will be used.
The IDE will also search for a matching `-src.jar` or `-sources.jar` to attach source code information to jars.

External (third party) jars are supported.
Support exist for [rules_jvm_external](https://github.com/bazelbuild/rules_jvm_external) as well as `java_import` based solutions (eg.[jvm_import_external](https://github.com/bazelbuild/bazel/blob/master/tools/build_defs/repo/jvm.bzl)).


### Runtime Classpath

The runtime classpath is different from the compile classpath.
It is required when launching JUnit tests from within the IDE or attaching a debugger to a running Bazel binary.

In order to compute the runtime classpath a Bazel command will be executed with IJ Aspects to obtain the list of all runtime jars (including transitives).
There is limited support to also resolve projects used from the runtime classpath.

The IDE may not be immediately usable for debugging while the Bazel command is still running.
Especially when source code is missing the IDE may attempt too repeat runtime classpath resolutions.
If you discover such a situation please don't hesitate and open an issue with a reproducible case.

