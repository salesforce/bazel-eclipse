## Conforming Java Packages

In various places in our Bazel Eclipse Feature documentation, we reference the term **Conforming Java Packages**.
This page defines that term.

### History: Maven and Configuration by Convention

Most people working with Java in Bazel have previously used Maven to build their Java.
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

But those conventions also helped the IDEs.
Having strong conventions makes it easier to write and test IDE features.
If the feature code works for one Maven project, it has a good chance of working for others.

### Today: Bazel and Flexibility

Bazel is a lot more flexible than Maven.
Depending on your point of view, that is a great thing.
If you are an IDE feature developer, this is not a great thing.

In a single logical 'module', Bazel can:

- have multiple BUILD files
- have multiple Java source locations
- have multiple resource file locations
- there is no enforced naming conventions for the source locations

While eventually we hope to support all of this in the Bazel Eclipse Feature, for now we have news:

```
To constrain permutations and simplify development, this feature currently officially
supports importing only Conforming Java Packages.
```

### Definition: Conforming Java Packages

*Conforming Java Packages* are [packages](https://docs.bazel.build/versions/master/build-ref.html#packages) in Bazel
  that are constrained in their layout and configuration.
They more or less adhere to Maven-like conventions, which in turn makes IDE support simpler.

The term refers to packages in Bazel with these characteristics:

- represent a logical 'module' of work (akin to Maven module)
- source code is written in Java
- contain a single top-level BUILD file (akin to the pom.xml)
- the package is not rooted directly in the workspace directory (i.e. beside the WORKSPACE file) but in a subdirectory
- default target (the one with the same name as the package) is the *java_library*/*java_binary* rule
- *java_test* rule(s) follow a naming convention: the rule name(s) must end in *test* or *Test*
- *src/main/java* contains the library source code to be packaged in the jar
- *src/main/resources* contains resources to be packaged in the jar
- *src/test/java* contains the test code
- *src/test/resources* contains resources to be used by tests
- no other Java source code outside of those locations exists within the package
- all *.java* files in the project are meant to be compiled (i.e. has globby \*\*/\*.java in BUILD)
- all *.java* tests in the project are meant to be run (i.e. has globby \*\*/\*Test.java in BUILD), although @Ignore annotations in the tests will be honored

While these conditions impose some restrictions, these greatly simplify the development of the IDE feature.
