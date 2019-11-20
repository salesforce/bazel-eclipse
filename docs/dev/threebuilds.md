## A Tale of Three Build Systems

This project is currently maintained using three independent build systems.
Two are run from the Eclipse SDK, and the Bazel build is run from the command line.
At all times, all build systems are expected to work.

- [Bazel Build](#build-1-bazel-build-system)
- [Eclipse SDK - PDE/JDT Build](#build-2-eclipse-sdk-pdejdt-build-system)
- [Eclipse SDK - Export Feature Workflow from Eclipse SDK](#build-3-export-feature-workflow-from-eclipse-sdk-ant-build)

The [Dev Guide](dev_guide.md) explains how to set up the Eclipse SDK and Bazel.
This document explains how they work together.

The two Eclipse SDK builds share their configuration files, but the Bazel
  build uses an independent set of configuration files for its build.
**We must try to keep them in sync**.
This document explains how this is done.

### Build System Futures

As you will read below, each build system has it's use and value.
As it stands, we have a workable solution and it is good enough to release our first versions of the feature.
But there are open issues and problems, so our build story is not fully written yet.
We expect to revisit our build after we get further down the road.

Specifically, we need to decide if we want to invest more time and effort into solidifying the Bazel command line build or switch to [Tycho](https://www.eclipse.org/tycho/documentation.php).
Tycho is the standard way to build Eclipse features/plugins from the command line.
While it is honorable to use Bazel to build the feature, we are leaning towards moving to Tycho.

### Build 1: Bazel Build System

It seems obvious that Bazel would be used to build a Bazel focused project, and so we do.
This is the build system that runs our tests.
It is a command line build, and is the build system used by CI.

```
bazel build //...
bazel test //...
```

The Bazel build is maintained in the BUILD files, as you would expect.
It is mostly managed like any Bazel project except for the Eclipse API jars (details below).

The Bazel build is supported by custom rules written found in [this Bazel package](../../tools/eclipse).
These originally came from the Google Eclipse plugin project, and we have evolved them as needed.

**Maven/Nexus Jars**
Do not add Maven jars to the *WORKSPACE* file using *maven_jar*.
We need to carefully manage external jars so that we keep all build systems in sync.
Follow the instructions in these projects:

- [Main Dependencies](../../plugin-libs/plugin-deps)
- [Test Dependencies](../../plugin-libs/plugin-testdeps)

**Eclipse API Jars**
The Eclipse API jars are embedded in our Git repo, and pulled into the Bazel build using *java_import* rules.
See the [//tools/eclipse_jars package](../../tools/eclipse_jars) for details about how this is done.

**Bazel Built Update Site**
While we have Bazel rules for building the Eclipse feature updatesite binary, we no longer use Bazel for this.
See [this package](../../tools/eclipse_updatesite) if you would like to see those rules.


### Build 2: Eclipse SDK PDE/JDT Build System

This is the build system we use when doing interactive development from the Eclipse SDK.
It allows us to get all the nice things an IDE can offer (code completion, immediate feedback for compile errors, etc).
It also allows us to launch and debug the feature.

It is configured mainly through the *plugin.xml*, *feature.xml*, and *build.properties files*.

**Inter-Project Dependencies in the Eclipse SDK**
Notice that the feature is spread across multiple Eclipse projects (one feature, and multiple plugins).
To make that work, we had to do a number of steps.
Also, if you want to add a new Eclipse project as a [plugin](../../plugin-libs),
  and add it to the feature, do those same steps:

For the new plugin project:
- Be sure to setup the test classes/deps as *Test Only*, which is a toggle in the *Build Path* wizard
- You have to make your project OSGi aware. Add a *META-INF/MANIFEST.MF* file to your project and copy the patterns seen in other plugin libs.
- You will need *plugin.xml*, *plugin.properties*, and *build.properties* files. Copy the existing patterns.
- Add the *Plug-in Development* nature to the project

To depend on that new project from *plugin-core*:
- Choose *Configure Build Path* on *plugin-core*
- Click on the *Java Build Path* item in the left nav
- On the *Projects* tab, choose *Add...* and then pick the new project to depend on
- Click on the *Project References* item in the left nav
- Check the box next to the new item

**Maven/Nexus Jars**
Do not add free form jars to the Eclipse projects.
We need to carefully manage external jars so that we keep both build systems in sync.
Follow the instructions in these projects:

- [Main Dependencies](../../plugin-libs/plugin-deps)
- [Test Dependencies](../../plugin-libs/plugin-testdeps)


### Build 3: Export Feature Workflow from Eclipse SDK (Ant build)

Because 3 is better than 2, there is a third build system at work.
It also is run from the Eclipse SDK, just like the PDE/JDT build.

This build is used when you export the feature (with plugins) as a .zip file.
This .zip file can then be used as an updatesite, and installed into plain Eclipse instances.

This workflow uses the configuration files from the PDE/JDT build as the base, but it does a lot more steps and so it requires additional configuration files.
Under the covers, this build uses Ant which means we have *build.xml* files.

Normally, you do not have to be aware of Ant if the Export operation is successful.
But if it fails, you will need to diagnose the *build.xml* files and determine a solution.

**Export the Bazel Eclipse Feature as an updatesite zip file from Eclipse SDK**

To export the .zip archive of feature, follow these steps:
- In the *Project Explorer*, right click on the *Bazel Eclipse Feature* project
- Choose *Export...*
- Choose *Plug-in Development* > *Deployable features*
- Make sure the Bazel feature is selected, and choose an output location for the *Archive File* option
- **Choose the category**: this step is easy to miss, but [creates a problem for the users](https://github.com/salesforce/bazel-eclipse/issues/9) if you skip. Switch to the *Options* tab, and click the *Browse* button for category. Choose *category.xml - Bazel Eclipse Feature* option. 
- Click *Finish*

After a little while, it should write out the .zip file successfully.
If this is the case, you don't need to worry about Ant.

But if it doesn't complete successfully, it will likely show an error dialog with the error,
  including the line number in a *build.xml* file.
Unfortunately, the wizard will then delete the *build.xml* file, so you will not have a copy
  to refer to.
To diagnose the problem, continue to the next section...

**Generating the Ant build.xml files**

If there is a problem with the Export build, you will want to investigate it using the *build.xml* files.
To do this, you first must manually generate them, as the Export build will
  not persist the *build.xml* files it uses.

Here are the general instructions for generating *build.xml* files:
- [Generating Ant build files](https://help.eclipse.org/kepler/index.jsp?topic=%2Forg.eclipse.pde.doc.user%2Ftasks%2Fpde_version_qualifiers.htm).

However the documentation is missing a few details.
Here are the actual steps to generate the *build.xml* for a project:
- Right-click on the *feature.xml* or *plugin.xml* for the project in the *Project Explorer*
- Choose *Plug-in Tools* > *Create Ant Build File*
- The *build.xml* file should be written into the project directory. Check the Eclipse error log if not.

:fire: If the *Plug-in Tools* option is missing, that means the project is missing the
  *Plug-in Development* nature.

:fire: Note, there is a gotcha when generating the *build.xml* files.
The wizard will fail silently in some cases, or at best quietly write an entry into the Eclipse error log.
Usually it means that you are missing a required file in the feature/plugin project.
For example, there must be a *META-INF/MANIFEST.MF* file in the project.
Such a file has been added to the [feature](../../feature) project to prevent this issue, even though a
  feature doesn't normally need that file.
Other files such as *build.properties* will also fail the generation process if missing.

:fire: be aware that the *build.xml* files will get deleted if you run an *Export* command on the project(s) from the Eclipse SDK.


**Running the Ant build**

After you have generated the Ant build files for the feature and plugins,
  you can run them from the command line.
As you will see, this currently does not work to completion.
But it can still be useful, as it should get through about half of the build before it fails.
If you have basic configuration issues, you might be able to resolve them by testing from
  the command line.

```
cd feature
ant build.update.jar
```

:fire: currently the Ant build does not fully work because of this issue:
[failed to create task or type eclipse.versionReplacer Cause: The name is undefined.](http://eclipse.1072660.n5.nabble.com/Export-Eclipse-Plugin-Error-td101962.html)
