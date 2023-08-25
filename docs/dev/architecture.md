## Bazel Eclipse Feature Architecture

### It's a Feature not a Plug-in

In the Eclipse world, a *plug-in* is a low level artifact roughly equivalent to a *jar* in the Maven world.
Just like you rarely think of jars as applications, an Eclipse plug-in is not normally a self-standing piece of functionality.

Eclipse plug-ins are typically collected into a *feature*, and distributed as a monolithic component.
As such, what this repository builds is the Bazel Eclipse **Feature**, not the Bazel Eclipse **Plugin**.

### Eclipse Features are Installation Scoped

This seems obvious, but when troubleshooting installations it is nice to have confirmation.

```
Feature installations are done into an Eclipse installation, not into a workspace.
```
You may have multiple Eclipse workspaces associated with a single Eclipse installation, but nothing about the install is workspace scoped.


### Concept Mappings

It is useful to explain how terminology maps between the Bazel and Eclipse worlds when using the Bazel Eclipse Feature.

- Many Bazel **workspaces** can be imported into an Eclipse **workspace**
- Each Bazel **workspace** is represented as an Eclipse **project** (*the workspace project*)
- A Bazel **target** (or **package**) is represented as an Eclipse **project** (*a package/target project*)
- The Eclipse **workspace** must not be created within a Bazel **workspace**
- The main driver for what's visible in the IDE (from a Bazel **workspace**) is a project view (`.bazelproject`).


### How the Bazel Eclipse feature fits in with JDT

:point_right: note that this section is written as the feature currently exists, which is a Java-only
Eclipse build feature. Although support for additional languages is possible it's not a priority.
The question that has to be answered is whether it makes sense. Typically a language is developed with tools
written in the same language.

The Eclipse [JDT](https://www.eclipse.org/jdt/) (Java Development Tools) are the set of components that run inside of Eclipse to support Java development, regardless of the build technology (Maven, Ant, Bazel).
The JDT components fulfill features such as:

- Syntax highlighting
- Code completion
- Incremental code compilation
- Search
- Debugger support
- Code formatters

But to do this, the JDT components need to be provided:

- Organization of Java source files into Java project
- Classpath for each such projects
- Libraries for such projects
- Target JDKs and other environment config

For this, they rely on build-specific features/plug-ins like this Bazel Eclipse feature and [M2Eclipse](http://www.eclipse.org/m2e/).
The Bazel Eclipse Feature sits alongside the JDT components in Eclipse to be the bridge between Bazel Java projects and the JDT.
It hooks into Bazel to compute classpaths and find Java source files.

![Bazel Eclipse Feature and JDT Architecture Diagram](BEF_Arch.png)


### Invokes command line Bazel to perform the build

When a user saves a file, the Bazel Eclipse Feature has a hook into Eclipse to be notified.
When notified, the feature delegates the build operation to command line Bazel.
It actually constructs a Bazel command line pattern, and invokes it in a shell.
The output streams back to the Console view.

Because it does the build in this way, it honors your *.bazelrc* file and other local config.
Additional configuration can be provided via project views.

There is a competition going on between Eclipse wanting to compile the Java source code and Bazel.
We strive a balance and may not invoke Bazel every time.

On some operating systems (hello MacOS) Eclipse may be launched with a different environment that a shell/terminal.
This will confuse Bazel and cause cache misses because of changing cache keys (eg., PATH environment).
The Bazel Eclipse Feature does an attempt to minimize this but may not be successful.


### Uses Bazel Query and Aspects to introspect topology

The feature has to compute classpath information for the Bazel targets.
It does this so that JDT can know what classes are available for code completion, and the incremental compiler can flag issues with red squigglies.
One way to do this in Bazel is to use Bazel Query, which is a tool that is included in Bazel.
This tools is handy for ad-hoc queries from the command line.
The feature uses it when discovering the project structure for Eclipse.

The IntelliJ [Aspect](https://docs.bazel.build/versions/master/skylark/aspects.html) is used for obtaining classpath information.
This requires performing a **successful** Bazel build **with** the aspect.
This may not always be possible.
Therefore the feature needs to implement some kind for recovery.


### Opinionated Integration

While Bazel considers itself the source of truth of everything, The Bazel Eclipse Feature ignores that occasionally.
The goal of BEF is a good DX.
Sometimes invoking a Bazel command for obtaining some information is just not right from a DX/UX perspective.
In this case BEF might implement some things directly rather then asking Bazel for it.


### Bazel Classpath Container

The bridge between JDT and the Bazel Eclipse Feature is largely accomplished by the Bazel Classpath container.
You can see it in action by visiting the *Java Build Path* properties for each imported package.
The *Libraries* tab will show an entry called *Bazel Classpath Container* on the classpath of the Eclipse project.
Inside of that is a dynamic list of dependencies that are used by the targets in the workspace.


### Bazel Java SDK

The SDK is a low level project without dependency on Eclipse APIs.
It contains infrastructure for launching Bazel command with the Java ProcessBuilder API.
It also contains code duplicated from the Bazel IntelliJ plug-in for working with the IntelliJ aspect.


### What does Maven do?

It makes sense to look over the fence at the Maven plug-ins to see how they have implemented the same functionality.

- [M2Eclipse plug-ins](https://github.com/eclipse-m2e/m2e-core)

Quite a lot code can be re-used from existing plug-ins.


### The plug-ins that compose the Bazel Eclipse Feature

Internally, the Bazel Eclipse Feature is implemented using several Eclipse plug-ins.
It does this separate concerns, and shares code between the UI integration (Eclipse IDE) and the language server (Eclipse JDT LS).
The Eclipse plug-ins have access to Eclipse APIs depending on their scope.

- **com.salesforce.bazel.eclipse.core**: plug-in is supposed to be headless only, i.e. must not access anything in `org.eclipse.swt`, `org.eclipse.jface` and `org.eclipse.ui`.
- **com.salesforce.bazel.eclipse.ui**: plug-in with integrating into Eclipse IDE (UI)
- **com.salesforce.bazel.eclipse.jdtls**: plug-in with integrating into Eclipse JDT LS
- **com.salesforce.bazel.sdk**: basic infrstructure and code without dependencies on Eclipse APIs.
