## Known Build and Developer Issues

### Bazel Build Errors and Warnings

**'//feature:bazeleclipsefeature' depends on deprecated target '@local_jdk//:java'**

This is caused by a Bazel rule we import from a public git repo using an older form of the Bazel API.
We cannot fix it easily.
It is benign at this point.

### Eclipse SDK Build Errors and Warnings

**Classpath Errors in the Eclipse SDK build**

If you have classpath-like build errors after importing the project, double check that you are running
  Eclipse SDK (which contains the PDE - Plugin Development Environment).
If you try to use a regular *Eclipse IDE for Java* it will not work.

**The JRE container on the classpath is not a perfect match**

We target an older version of Java [intentionally](jdk.md).
But if you launch your Eclipse SDK with a newer version of the JRE/JDK, it will worry
  about it with this warning.

We don't distribute a fix for this, as it involves files that should not be in source control.
But it is simple to eliminate:

- For each affected project, right click on the project in the Project Explorer and choose Properties
- Navigate to *Plug-in Development* -> *Plug-in Manifest Compile*
- Set *Incompatible Environment* to *Ignore*

### Debugging from Eclipse SDK Issues

These are issues you might face when trying the debug/run the feature from the Eclipse SDK.

**Caused by: java.lang.UnsupportedClassVersionError: x/y/z/SomeClass has been compiled...**

This could happen if the [jdk version configuration](jdk.md) for the project is not applied correctly.
You have built the feature with a compiler that is generating class files for a newer version of the JDK
  than we are targeting.
