## Conforming Java Packages

In the early days of Bazel Eclipse, we had a number of restrictions for the file structure of each Bazel package.
We required it to conform to the Maven standards of storing source code in _src/main/java_ and _src/test/java_.

As of Bazel Eclipse 1.5, these restrictions have been lifted.
We believe BEF properly handles any reasonable file system layout for Java packages.

The only caveat is for test source files.
We mark a class as being on the Eclipse project test classpath if it lives within a directory that starts with the word 'test'.
For example, these files would be considered test classes:

- src/test/java/com/salesforce/foo/FooTest.java
- source/test/com/salesforce/foo/FooTest.java
- src/tests/bar/com/salesforce/foo/FootTest.java
- my/blue/testsAndUtils/com/salesforce/foo/Foo.java

In Eclipse, test classes are placed on a test classpath, which is distinct from the main classpath of a project.
