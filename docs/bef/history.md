## History and Credit for Bazel Eclipse Feature

### Salesforce

The project is sponsored by Salesforce.
Salesforce has opened the repository as open source as of November 20, 2019, with contributors located here:

<!-- markdown-link-check-disable-next-line -->
- [Bazel Eclipse Feature contributors](https://github.com/salesforce/bazel-eclipse/settings/collaboration)

For more information about open source at Salesforce, see this page:

- [Salesforce Open Source](https://opensource.salesforce.com/)

### Origins

This project began as a hackathon project of Peter Laird (@plaird) at Salesforce in 2018.
It was seeded by a fork of the [Google Bazel Eclipse plugin](https://github.com/bazelbuild/eclipse)
  aka *e4b*, which had been abandoned.
The last commit taken from that repo was *dc387fa035edca898fc5ffc80519f79f6d2d4f78* from April 2018.

The plugin (now evolved into a *feature* consisting of a set of plugins) has been heavily modified since the original fork.
Almost nothing remains of the original Google code.
The idea to use a Bazel Aspect implementation to compute package dependencies is the main surviving element
  of the original Google implementation.

### Open Source and Continued Development

Starting in 2019, a small team within Salesforce took up development of BEF as an unfunded side project,
  which continues to this day (Peter Laird, Simon Toens, Blaine Buxton, Nishant D'Souza, Di Sang).
BEF is almost entirely a passion project - most hours are invested from personal time on evenings and weekends.

In 2023, the project experience a major rewrite to re-use as much code as possible from the Bazel IntelliJ plug-in and better integrate with Eclipse APIs.

### Bazel Java SDK

BEF was architected from the early days recognizing that most of the code required for BEF is actually not
  specific to Eclipse.
While that code was born inside of BEF, it was kept as a separate Eclipse plugin with the intention to split
  it out as a separate top level project - the Bazel Java SDK.
The formal separation occurred in early 2021.
In 2023 the decision was made to discontinue with the abstraction.
Instead priority is now a focus on better and efficient integration with proper Eclipse APIs.

### Build Implementation History

The original build from the Google *e4b* plugin was implemented in Bazel using custom rules.
This was good enough to get a rough binary produced, but nothing more.
Many Eclipse plugin metadata requirements were not honored.
@plaird fixed some of it, but gave up and retreated to use Bazel only to run command line build/tests,
Packaging of the actual BEF binary was done via Eclipse *Export* within the Eclipse SDK.

In 2021, Gunnar Wagenknecht (@guw) used his Eclipse expertise to reimplement [the build](../dev/thebuild.md)
  using Maven Tycho.
Tycho is the de facto standard for Eclipse plugins, and allowed us to switch to command line builds
  for the binaries.
Never mind the blasphemy of building a Bazel-focused product with Maven.
Gunnar also contributed the initial GitHub Actions CI solution, which builds the online update site
  in GitHub Pages.

### Aspect Implementation

BEF/SDK gathers dependency graph information using a Bazel Aspect.
The aspect implementation comes from the [Bazel IntelliJ plugin](https://github.com/bazelbuild/intellij) project.
We copy over a newer copy from time to time and integrate it into BEF/SDK.

### Attribution

All files from the original repository have been modified to some extent, and most were created after the initial fork.
Therefore all files carry at least a notice like this:
```
Copyright 2019-2021 Salesforce. All rights reserved.
```

Files and code blocks that originated in the original fork carry the following notice, with an AUTHORS file that
  identified *The Bazel Authors* as Google Inc.
  (specifically Damien Martin-Guillerez and Dmitry Lomov).
```
Copyright 2016 The Bazel Authors. All rights reserved.
```

The Bazel Import Wizard UI was adapted from a similar wizard in the [M2Eclipse](https://www.eclipse.org/m2e/) project,
  which has this notice:
```
Copyright (c) 2008-2018 Sonatype, Inc. and others.
```

Formal documentation of the above is contained in the [NOTICE](../../NOTICE) file.
