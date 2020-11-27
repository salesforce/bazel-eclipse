## Using the Bazel Eclipse Feature: Workspace Import

To work on your Bazel Workspace you must first import it.
Note that there is no IDE support for creating a new Bazel Workspace from the IDE, it must already exist.

### Limitations

There are a set of known limitations and known issues with Bazel package import.
The work item that will address each limitation is linked from each section.

#### Limitation: Only one Bazel workspace can be imported in an Eclipse workspace

At this time, the feature only supports a single Bazel workspace in an Eclipse workspace.
If you do development in multiple Bazel workspaces, you will need to have multiple Eclipse workspaces.

This issue tracks this limitation: [Support multiple Bazel workspaces in a single Eclipse workspace](https://github.com/salesforce/bazel-eclipse/issues/25)

#### Limitation: Global Java Compiler Compliance Level is not Updated

Note also that BEF does not currently configure the *Eclipse Compiler Preferences* during import, which is a global setting for the Eclipse workspace.
If you launch your Eclipse using JDK8, the default *Compiler Compliance Level* will be set to JDK8 regardless of your Bazel workspace.
You can manually change this in the Eclipse preferences in *Eclipse -> Preferences -> Java -> Compiler*.
This will be improved with [Update Eclipse workspace Java compiler compliance level based on Bazel workspace .bazelrc](https://github.com/salesforce/bazel-eclipse/issues/184).

#### Limitation: Only the Bazel Workspace JRE Source Level is Honored

Currently, only the Bazel workspace global *javacopt* setting will be honored when configuring the JRE for each imported project.
If not present, JRE 11 will be assumed.
For example, if this is in your *.bazelrc* file, JRE 8 will be in every Eclipse project's build path after import:

```
build --javacopt=-source 8 -target 10
```

After import, you are free to [change the JRE](using_the_feature_classpath.md) for each project using the Eclipse *Build Path* user interface.
Please see [Support package level JDK configuration for Build Path](https://github.com/salesforce/bazel-eclipse/issues/89) for status on improvement.

#### Limitation: Only Import Conforming Java Packages

Before you proceed, please note that at this time the feature is not robust enough to build just any Bazel package.
The feature only supports what we call *Conforming Java Packages*.

Please see the [Conforming Java Packages explainer](conforming_java_packages.md) for more details.

#### Known Issue: Import of a Bazel Workspace is Slow

Be aware that importing a large number of Bazel packages into Eclipse is slow.
BEF currently computes the classpath for each Java rule in each imported package.
Because Java packages tend to have many *java_test* rules, this can take a long time.
This is done [for specific reasons](https://github.com/salesforce/bazel-eclipse/issues/29), but is ripe for optimization.

This issue is tracked as: [Improve performance of Bazel workspace import](https://github.com/salesforce/bazel-eclipse/issues/4)


### Steps to Import Your Bazel Workspace into Eclipse

Before you can import the Bazel workspace, you **must** run a command line build of the full workspace.
Import will fail if there are build errors in the workspace because import uses metadata computed
  from the BUILD files.

```
bazel build //...
```  

Then, in the IDE, the flow for import matches the familiar Maven import process:

- *File* -> *Import...*
- *Bazel* -> *Import Bazel Workspace*
- Click the *Browse...* button, and navigate to the *WORKSPACE* file for your Bazel workspace
- The feature will then recursively scan that directory looking for BUILD files with *java* rules
- The scanned results will appear in the *Bazel Java Packages* tree view.
- Click the packages that you would like to have in your Eclipse Workspace.
  - Note, you do not need to have transitive closure of upstream dependencies in the Eclipse Workspace.\*
- Click *Finish*, and your Bazel packages will be imported as Eclipse projects.

\* Example: if you have two Java projects in your workspace *LibA* and *LibB*, and *LibB* depends on *LibA*. You can import just *LibB*. The feature will set the classpath correctly to consume the built *LibA* artifact.

### Project Views (optional)

The Bazel Eclipse Feature has implemented support for the [Project View](https://ij.bazel.build/docs/project-views.html) file from the IntelliJ Bazel plugin.
A Project View file allows a team to share a common list of Bazel packages to import into the IDE.

When working on a large Bazel workspace, this is a more convenient way of onboarding a new team member, as a team will likely only work on a small subset of packages in the Bazel workspace.
Manually locating those packages in the import tree control UI would be tedious.
It is easier to point the new team member to a Project View file to use for their Bazel workspace import.

To use an existing Project View file during import:
- Browse to your *WORKSPACE* file as described in the instructions above
- If Bazel packages are already selected in the tree control, click the *Deselect All* button
- Click the *Import Project View* button below the tree control and navigate to the Project View file for your team
- Click *Open*, and BEF will select the proper Bazel packages in the tree control

Currently, BEF supports the *directories* and *targets* stanzas.
Please consult the IntelliJ documentation linked above for details on how to author a Project View file.

### Next Topic: Manage your Project Configuration

The next page in our guide discusses the [Project Settings features](using_the_feature_settings.md) of the Bazel Eclipse Feature.
