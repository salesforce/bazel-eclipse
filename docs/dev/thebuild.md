## How the Build Works

This project is currently maintained using one build.
Technically, there are still two build systems - Eclipse PDE and Maven Tycho.
However, they use one single source of truth - Eclipse PDE.
Thus, you don't have to worry about maintaining Maven POM.xml files.



### Eclipse PDE (SDK and Self-Hosting)

Self-hosting is the development mode we use when doing interactive development from the Eclipse SDK.
It allows us to get all the nice things an IDE can offer (code completion, immediate feedback for compile errors, etc).
It also allows us to launch and debug the feature.

It is configured mainly through the *MANIFEST.MF*, *plugin.xml*, *feature.xml*, and *build.properties* files.

#### Target Platform
The Target Platform defines what we develop and build against.
It's maintained in the [releng/target-platform](../../releng/target-platform) project.

You need to open the *target-platform.target* file and set it as the *Active Target Platform* (link in upper right).
Once this is done, the development environment is ready.

Note, there are two target-platform files: *target-platforms.target* and *target-platforms.tpd*.
The *target-platforms.target* file is the XML file used by Eclipse PDE and Tycho.
The *target-platforms.tpd* file is a convenience file with its own DSL (domain specific language) that saves us from writing XML and provides a content assist.
Only the *target-platforms.tpd* file should ever be modified.
To generate the *target-platforms.target* file one needs to right click on the *target-platforms.tpd* and select *Create Target Definition File* or *Set as Target Plaform*.
In order to edit the files, the [Target Platform Definition DSL and Generator](https://github.com/eclipse-cbi/targetplatform-dsl) plug-in must be used.

#### Plug-ins
Plug-ins are the *"jars"* in Eclipse.
They are OSGi bundles.

You can use the new Plug-in wizard in Eclipse to create a new plug-in.
It should be created in the [bundles](../../bundles) folder.

The classpath of plug-ins is managed entirely via *MANIFEST.MF* file.
Instead of hand-editing the file you should use the PDE Plug-in file editor.

The build path in Eclipse should be the PDE default, i.e. with the "Plug-in Dependencies" container.

#### Tests
Tests for a plug-in are maintained in a separate project in the [tests](../../tests) folder.
Typically the tests project is not a Plug-in project but a Fragment project.
A Fragment attaches to a *host* bundle (plug-in) and can access package private classes.


To depend on that new project from *plugin-core*:
- Choose *Configure Build Path* on *plugin-core*
- Click on the *Java Build Path* item in the left nav
- On the *Projects* tab, choose *Add...* and then pick the new project to depend on
- Click on the *Project References* item in the left nav
- Check the box next to the new item

#### Maven/Nexus Jars
Do not add free form jars to the Eclipse projects.
Instead check if it is already available based on the Target Platform.
If not you should check if its available in [Eclipse Orbit](https://download.eclipse.org/tools/orbit/downloads/).
Anything from Eclipse Orbit can be added to the Target Platform.

You should then use *Import-Package* to add the dependency reference to the *MANIFEST.MF* file.
A proper version constraint (just lower bound, i.e. number) ensures a minimum version will be resolved.

#### Import-Package vs. Require-Bundle
Our rule of thumb is:
 - *Require-Bundle* for inter-project dependencies, i.e. dependencies to Eclipse plug-ins and to plug-ins in our project.
 - *Import-Package* for third-party Maven/Nexus jars

 Don't bother using version ranges.
 They complicate things unnecessarily.
 Use a lower version bound on *Import-Package*.

 Because we do not use *Import-Package* between our plug-ins we do not set versions on exported packages.
 If you believe we should, please raise an issue.
 However, we do not expect anyone to provide alternate implementations of our packages.

### Release the Feature

We have a dedicated document that explains the release process:
- [Releasing Bazel Eclipse](release.md)
