## Using the Bazel Eclipse Feature: Workspace Import ![BEF Logo](../logos/bef_logo_small.png)

To work on your Bazel Workspace you must first import it.
Note that there is no IDE support for creating a new Bazel Workspace from the IDE, it must already exist.

### Prerequisite: Project Views

Please read [all about Project Views and how they work](../common/projectviews.md) to understand how projects are created in Eclipse.


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
