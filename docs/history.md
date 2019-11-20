## History and Credit for Bazel Eclipse Feature

### Salesforce

The project is sponsored by Salesforce.
Salesforce has opened the repository as open source as of November 20, 2019, with contributors located here:

- [Bazel Eclipse Feature contributors](https://github.com/salesforce/bazel-eclipse/settings/collaboration)

For more information about open source at Salesforce, see this page:

- [Salesforce Open Source](https://opensource.salesforce.com/)

### Origins

This project began as a Salesforce private fork of the [Google Bazel Eclipse plugin](https://github.com/bazelbuild/eclipse) aka *e4b*,
  which had been abandoned.
The last commit taken from that repo was *dc387fa035edca898fc5ffc80519f79f6d2d4f78* from April 2018.
The plugin (now evolved into a *feature* consisting of a set of plugins) has been heavily modified since the original fork.

The Bazel Aspect implementation that computes package dependencies, and the code for the Bazel classpath container are the main surviving elements of the original implementation.
The code has been modified, but the original ideas remain the same.
Based on code comments, we believe the Aspect originally came from the [Bazel IntelliJ plugin](https://github.com/bazelbuild/intellij) project.

### Attribution

All files from the original repository have been modified to some extent, and many were created after the initial fork.
Therefore all files carry at least a notice like this:
```
Copyright 2019 Salesforce. All rights reserved.
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

Formal documentation of the above is contained in the [NOTICE](../NOTICE) file.
