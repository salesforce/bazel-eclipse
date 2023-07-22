# Bazel Java Language Server

This is the home of the Language Server project docs.

There is a BazelCon presentation you can watch that covers the ideas.
Start at the 12 minute mark to see the Language Server portion of the talk:
- [Eclipse and VS Code IDE Support for Java packages in Bazel](https://www.youtube.com/watch?v=oLnfv2-aGwk)


## Architecture & Features

The Bazel Java Language Server (BJLS) is a plug-in for Eclipse, which extends the [Eclipse Java Language Server](https://github.com/eclipse/eclipse.jdt.ls) with project import and classpath resolution capabilities for Bazel workspaces.

For more details about the supported Java features please see:
- [Eclipse Java Language Server](https://github.com/redhat-developer/vscode-java)
- [Language support for Java for Visual Studio Code](https://github.com/eclipse/eclipse.jdt.ls)

## VS Code Support

You **cannot** *install* the BJLS into VS Code.
You have to install an extension for your editor/IDE bundling it.

We are working on integrating it directly into [Language support for Java for Visual Studio Code](https://github.com/eclipse/eclipse.jdt.ls).
Until then please use [Bazel support for Java for Visual Studio Code](https://github.com/salesforce/bazel-vscode-java).
