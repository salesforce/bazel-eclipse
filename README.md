# Bazel Eclipse Feature

This is the Eclipse Feature for [Bazel](http://bazel.io).
The Bazel Eclipse Feature supports importing, building, and testing Java projects that are built using the Bazel build system.

This project is supported by Salesforce.

## Feature Status and Roadmap

Active development of this feature is currently being done by a team within Salesforce.
You can track our past/current/future work using these links:

- [bazel-eclipse roadmap](https://github.com/salesforce/bazel-eclipse/wiki)
- [bazel-eclipse issues](https://github.com/salesforce/bazel-eclipse/issues)

:fire: currently the feature is still in development and only works on small, Java-only workspaces. There are known scalability issues, and current development is only focused on Java projects.

## Using the Feature

For detailed installation and setup instructions, see this page:

- [Installing Eclipse and the Bazel Eclipse Feature](docs/install.md)
- [Bazel Eclipse Feature User's Guide](docs/using_the_feature.md)

## Developing the Feature

We welcome outside contributions.
As with any OSS project, please contact our team prior to starting any major refactoring or feature work,
  as your efforts may conflict with ongoing work or plans of ours.

To start developing this feature, follow the instructions on our Dev Guide.

- [Bazel Eclipse Feature Dev Guide](docs/dev/dev_guide.md)

To find known technical debt and known bugs that need work, please look at:

- [Tracked Issues](https://github.com/salesforce/bazel-eclipse/issues)
- TODO comments in the code base; smaller ideas are tracked using simple TODO comments

## History and Credit

This project began as a Salesforce private fork of the [Google Bazel Eclipse plugin](https://github.com/bazelbuild/eclipse), which had been abandoned.
Full history and credit is explained in the [history and credit document](docs/history.md).
