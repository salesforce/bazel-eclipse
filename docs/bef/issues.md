## Known Issues with Bazel Eclipse ![BEF Logo](../logos/bef_developers_small.png)

### Compilation Errors after Import

After importing one or more packages from your Bazel workspace into BEF, you may
  find that you have many build errors in your source files.
This can also happen upon restart of Eclipse.

This often happens if you have a build error in your Bazel workspace.
BEF needs to run a build to compute the dependency graph and collect other details
  about your workspace.
If the build fails, some of that information will not be available to BEF.

The best indicator that this has happened (for Java projects) is the _Bazel Classpath Container_
  is missing from the project in the Eclipse Package Explorer.
This is seen by expanding the project node in the explorer - there should be a node
  labelled _Bazel Classpath Container_ nested underneath.

### "One or more cycles were detected in the build path of project"

This issue can happen if you have this pattern in your workspace:

- ```//a/b/c:foo``` depends on ```//x/y/z:bar```
- ```//x/y/z:bar``` depends on ```//a/b/c:oops```

and you import both ```//a/b/c``` and ```//x/y/z``` as projects into BEF.
Bazel is happy with the above dependency graph because Bazel tracks dependencies
  at the target level (```foo => bar => oops```) which has no cycle.

The problem is Eclipse tracks dependency information at the Project level.
BEF maps each Bazel package as an Eclipse Project, as that is the most reasonable balance
  between fidelity to Bazel and usability.
Therefore Eclipse sees the dependency graph as ```c => z => c``` which is a cycle.

We will explore a permanent solution to this in [Issue 197](https://github.com/salesforce/bazel-eclipse/issues/197)
  but there might not be a 100% effective solution to this given we can't expect to make
  changes to Eclipse or JDT.

In the meantime, there are two workarounds:
- Only import one of the Bazel packages (```//a/b/c``` or ```//x/y/z```) into Eclipse to prevent the cycle.
- Delete the error from the _Problems View_ in Eclipse and the Error will not return often. Right click on the error line, and choose _Delete_.

### JDK Configuration Issues: "IllegalArgumentException: external/local_jdk" or "Unable to locate a Java Runtime."

See [this BEF issue](https://github.com/salesforce/bazel-eclipse/issues/417) for status and
  workarounds for these types of problems.

### "CreateProcess Error Code 5 Access Denied", "Error fetching repository" (Windows)

All Windows platform issues are documented on a dedicated page.
See our dedicated [Windows Guide](windows.md) for more details.

### The type [sometype] cannot be resolved. It is indirectly referenced from required .class files

Eclipse is greedy at traversing the classpath for all transitively referenced classes.
In some cases, you may not need a dependency at runtime (perhaps it is an optional feature of a library)
   so your target does not include it as a dep.
But Eclipse will see that you might *possibly* need it, and will complain.

The workaround is to add the dependency, even if your target may not need it.
There is also a [specific case of this for gRPC](https://github.com/salesforce/bazel-eclipse/issues/325),
   which has a particular workaround.

### Eclipse Titlebar Shows the IntelliJ Icon (Mac)

On Mac, when a file is open in an editor, Eclipse displays the icon for that
  file type based on the file association in the Finder.
This is incorrect behavior, but it is the way it works.
If you have .java files (or other code file types) associated with IntelliJ
  in the Finder, the IntelliJ icon will appear in the Eclipse titlebar.

To fix:
- Find a .java file (or other code file) on the file system with the Finder
- Right click on it and select *Get Info*
- In the Open With combo box, select the application that you would like to associate
  with that file type
- Click the *Change All...* button
- Reboot your computer

You may elect to use Eclipse as the default file association, but it is recommended
  to choose a lightweight editor instead.
When you click on a file in the Finder you generally want to take a look at it, not
  spawn an entire IDE environment.
This will cause the icon in the Eclipse IDE to still be mismatched, but it is less
  confusing than having the IntelliJ icon in the titlebar.
