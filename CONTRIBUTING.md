# bazel-eclipse Contribution Guide

We welcome any contributions.
Please review our [code of conduct](CODE_OF_CONDUCT.md).


## Eclipse Plug-In Development

The Bazel Eclipse Feature as well as the Bazel Java Language Server are developed as a set of Eclipse plug-ins.
We recommend you to learn about Eclipse feature and plug-in development in general.
Start with something simple before trying to tackle BEF or BJLS.

Recommended resources
- [What are Eclipse Plugins, Features, Products, Natures, etc.](https://stackoverflow.com/questions/2692048/what-are-the-differences-between-plug-ins-features-and-products-in-eclipse-rcp)
- [Build a Hello World plugin](http://www.vogella.com/tutorials/EclipsePlugin/article.html)


## Prerequisites

You need:
- latest version of [Eclipse IDE for Eclipse Committers](https://www.eclipse.org/downloads/packages/)
- JDK 17
- Bazelisk available as `bazel` binary in `PATH` environment

### Additional Plug-Ins from the Eclipse Marketplace:
- [AnyEdit Tools](https://marketplace.eclipse.org/content/anyedit-tools)
- [Yaml Editor](https://marketplace.eclipse.org/content/yaml-editor)
- [Bash Editor](https://marketplace.eclipse.org/content/bash-editor)
- [MoreUnit](https://marketplace.eclipse.org/content/moreunit)
- [Eclipse Zip Editor](https://marketplace.eclipse.org/content/eclipse-zip-editor)
- [Target Platform Definition Generator](https://github.com/eclipse-cbi/targetplatform-dsl)
- *Optional:* [macOS Eclipse Launcher](https://marketplace.eclipse.org/content/macos-eclipse-launcher)


## Setup Workspace & Import Projects

You must always import all projects for both BEF and BJLS.
Most code is in a core plug-in shared by both.
As a result, modifications in the core plug-in may need adaption in BEF as well as BJLS.

**After cloning the repository, follow these steps:**

- Launch the Eclipse IDE for Committers with a **new workspace**
- Ensure JDK 17 is configured (*Preferences > Java > Installed JREs*)
- Click *File > Import > General > Existing Projects into Workspace*
- Select the `bazel-eclipse` folder as the root directory
- Select **Search for nested projects** (critical step)
- Eclipse should detect that the projects are there, and offer to import them all (*checked* by default).
- Click *Finish* and wait

There will be errors.
This is expected at this point.
You need to setup the target platform next.

- Open file `bazel-eclipse/releng/target-platform/target-platform-dev.target`
- Click the *Set as Active Target Platform* link in the upper right (or *Reload Target Platform*)

This will run for a **long** time and download any necessary plug-ins/jars.
Once done everything should build.

:fire: If you see errors at this point, please [search/see discussions for help](https://github.com/salesforce/bazel-eclipse/discussions/categories/q-a).


## CI

We use [GitHub Actions](https://github.com/salesforce/bazel-eclipse/actions) for our CI system.

## How the Build Works

This is explained in more detail in the [build guide](docs/dev/thebuild.md).

