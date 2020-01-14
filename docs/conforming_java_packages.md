## Conforming Java Packages

In various places in our Bazel Eclipse Feature documentation, we reference the term **Conforming Java Packages**.
This page defines that term.

### History: Maven and Configuration by Convention

Most people working with Java in Bazel have previously used Maven to build their Java projects.
It is sometimes helpful to refer to the Maven experience when explaining Bazel concepts.
This is such a case.

In Maven, there are strong conventions for Java modules.
You should be quite familiar with what goes in these files and directories:

- *pom.xml*
- *src/main/java*
- *src/main/resources*
- *src/test/java*
- *src/test/resources*

In fact, mandating these conventions was a big innovation of Maven when it was first introduced.
Any developer can jump into a Maven project and know where to find the major things.

And those conventions also helped the IDEs.
Having strong conventions makes it easier to write and test IDE features.
If the feature code works for one Maven project, it has a good chance of working for others.

### Today: Bazel and Flexibility

Bazel is a lot more flexible than Maven.
Depending on your point of view, that is a great thing.
In a single logical 'module', Bazel can:

- have multiple BUILD files
- have multiple Java source locations
- have multiple resource file locations
- there are no enforced naming conventions for the source locations
- have multiple build outputs, each with a different dependency graph (e.g. classpath)

But if you are an IDE feature developer, this is not a great thing.
At least with Eclipse, the IDE features were not designed with all this flexibility in mind.
We have to find ways of mapping Bazel flexibility into existing, more rigid, IDE concepts.

While eventually we hope to support all of this in the Bazel Eclipse Feature, for now we have this news:

```
To constrain permutations and simplify development, this feature currently officially
supports importing only Conforming Java Packages.
```

### Definition: Conforming Java Packages

*Conforming Java Packages* are [packages](https://docs.bazel.build/versions/master/build-ref.html#packages) in Bazel
  that are constrained in their layout and configuration.
They more or less adhere to Maven-like conventions, which in turn makes IDE support simpler.

The term refers to packages in Bazel with these characteristics:

- represent a logical 'module' of functionality (akin to a Maven module)
- source code is written in Java
- the package is not rooted directly in the workspace directory (i.e. beside the WORKSPACE file) but in a subdirectory
- contains a single top-level module BUILD file (akin to the pom.xml)
  - the BUILD file contains at least one *java_library* target
  - the BUILD file may also contain zero or more *java_binary* and *java_test* targets
  - there are no conflicting versions of upstream dependencies in the java targets in the module BUILD file (e.g. Guava 15, 25)
- source files are in standard locations
  - *src/main/java* contains the library source code to be packaged in the jar
  - *src/main/resources* contains resources to be packaged in the jar
  - *src/test/java* contains the test code
  - *src/test/resources* contains resources to be used by tests
  - no other Java source code outside of those locations exists within the package
- all Java files are 'active'
  - all *.java* files in the project are meant to be compiled (i.e. has globby \*\*/\*.java in BUILD)
  - all *.java* tests in the project are meant to be run (i.e. has globby \*\*/\*Test.java in BUILD), although @Ignore annotations in the tests will be honored

While these conditions impose some restrictions, these greatly simplify the development of the IDE feature.
Over time, we hope to reduce these restrictions.
See the [BEF project planning epics](https://github.com/salesforce/bazel-eclipse/projects) for more information, especially these issues:

- [Relax src/main/... and src/test/... package layout requirements](https://github.com/salesforce/bazel-eclipse/issues/8)
- [Support Bazel Java modules with multiple BUILD files](https://github.com/salesforce/bazel-eclipse/issues/24)
- [Support multiple classpaths within a Java Eclipse project](https://github.com/salesforce/bazel-eclipse/issues/23)

### Recommended Java Conventions

In addition to the mandated conventions above, there are additional recommended conventions for Java repositories.

**Prohibit Implicit Test Dependencies**

Sadly, Bazel by default adds implicit, unspecified, dependencies to all *java_test* targets.
Currently, that dependency list include *junit* and *hamcrest* libraries.
There are ways for Bazel Eclipse Feature to support this (see [Issue #43](https://github.com/salesforce/bazel-eclipse/issues/43)) but it is best not to rely on that.

Fortunately, Bazel has a flag to disable this problematic behavior.
Before importing your Bazel workspace into Eclipse, we recommend fixing any instances of implicit Java dependencies.
Set [explicit_java_test_deps to true](https://docs.bazel.build/versions/master/command-line-reference.html#flag--explicit_java_test_deps) in your *.bazelrc* file:

```
build --explicit_java_test_deps=true
```

and then run ```bazel clean``` and then ```bazel build //...``` to find and fix all of the broken targets.
