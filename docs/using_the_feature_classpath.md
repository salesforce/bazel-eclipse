## Understanding the Java Classpath with the Bazel Eclipse Feature ![BEF Logo](logos/bef_logo_small.png)

BEF ultimately computes classpath information and maps it into the existing Java Developer Tools (JDT) plugins of Eclipse.
This works well, but there are some caveats to this mapping.
The caveats come from the fact that JDT grew up around the Maven build system, and Bazel is a more flexible build.

### Requires conforming Java packages

The top-level README links into a major set of limitations we have with mapping Bazel packages into JDT.
The limitations allow us to use JDT without modifications.

- [Conforming Java Packages](conforming_java_packages.md)

### Classpaths are scoped to each Eclipse project

Each Eclipse project maps to a single Bazel package.
For example, Bazel package *//projects/libs/apple-api* maps to an Eclipse project named *apple-api*.
There is a *Bazel Classpath Container* node inside of the *apple-api* project, that is the computed
  classpath from the Bazel BUILD file for the package.

### There are two classpaths per project

The *Bazel Classpath Container* actually configures two classpaths with JDT:
- the main classpath
- the test classpath

The way this is computed is as follows:

- any dependency within the package that only appears in *java_test* target(s) is added to the test classpath
- all other dependencies for any java rule in the package get added to the main classpath

### Configuring the JDK

Within Bazel, every Java rule could target a different JDK version.
However, BEF currently only supports a single global JDK setting for configuration during import.
It is configured by the source level set on the command line
  (or [.bazelrc file](https://docs.bazel.build/versions/master/guide.html#bazelrc-the-bazel-configuration-file))
  [javacopt](https://docs.bazel.build/versions/master/user-manual.html#flag--javacopt) option:

```
--javacopt "-source 8 -target 8"
```

While this is a limitation, this is only the default set during import.
You may then manually configure the JDK by configuring the Build Path of any Eclipse Project.

- Right click on the project in the *Package Explorer*
- Click *Build Path -> Configure Build Path...*
- Click the *Libraries* tab
- Click on the existing JRE or JDK entry, and then click *Remove*
- Click *Add Library... -> JRE System Library* and then make your choice

### Global Search Classpath

This feature provides a benefit when working with large Bazel workspaces.
With a large workspace, during import you will likely only choose to import a handful of packages.
BEF will configure an Eclipse project for each of those packages, with the proper classpath.

However, the Eclipse *Open Type* dialog will then only have visibility into the types found in the classpaths of the
  imported projects.
Types that are available in the Bazel workspace, but not in those projects, will not be visible to *Open Type*.
This makes it hard to discover useful types when doing development.

The *Global Search Classpath* is a feature to resolve this issue.
It scans the external jar directory of the Bazel build, and adds all found jars to a synthetic classpath node
  nested under the Bazel Workspace project in the Package Explorer.
By having them appear in a classpath object, JDT can now find all enclosed types.

To disable this feature do the following *before* importing packages into your workspace:  
- *Eclipse -> Preferences -> Bazel* (exact menu varies by platform)
- Uncheck the *Enable global classpath search* option.

Note that the feature may not work for all use cases.
See the [Global Search Classpath](https://github.com/salesforce/bazel-eclipse/issues/161) issue for status
  and open issues.

### Next Topic: Launching binaries and tests from Eclipse

The next page in our guide discusses the [Launchers](using_the_feature_launching.md) with the Bazel Eclipse Feature.
