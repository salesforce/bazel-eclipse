## Installing Eclipse and the Bazel Eclipse Feature

This page documents how to get up and running with Eclipse and the Bazel Eclipse feature.
These instructions are for **users that just want to use** the Bazel Eclipse feature.


### Supported Platforms

We will support you on:
- Mac - reasonably recent OS versions
- Linux - reasonably recent OS version of a major distro (Ubuntu, Mint, etc)
- Windows - best-effort, we are looking for expertise/help


### Supported Eclipse

We recommend the latest release of [Eclipse IDE for Java Developers](https://www.eclipse.org/downloads/packages/).
Given the language server is our priority, we may need to adopt new APIs quickly.

### Installing Bazel

The Bazel Eclipse Feature does not come with an embedded install of Bazel.
You must have Bazel binary (`bazel`) installed on your machine and in your shell path.

We recommend Bazelisk.


### Installing the Bazel Eclipse feature into Eclipse

**Manually Install the Bazel Eclipse feature into Eclipse:**

First, understand what releases of BEF are available, which is [covered on the Releases page](releases.md).
If you want to install the latest version of BEF, follow these steps to install it in your Eclipse IDE using the update site:

- Start Eclipse.
- In Eclipse, go to *Help* -> *Install New Software*
- Click the *Add* button
<!-- markdown-link-check-disable-next-line -->
- For the location, enter: **https://opensource.salesforce.com/bazel-eclipse/update-site**
- Give the location a name, like *Bazel-Eclipse updatesite*
- In the tree control area, open the *Bazel Eclipse* node
- Check the box next to the *Bazel Eclipse Feature* item, then hit *Next*
  - Note: BEF users do not need the *BJLS Feature*. You should leave that unchecked.
- Click *Next/Agree/Finish* until it completes.
- Restart Eclipse

Otherwise, you can install older versions of BEF using the archive zip files that are listed [on the Releases page](releases.md).

**To verify that it is installed:**
- Start Eclipse
- *Eclipse* > *About* > *Installation Details* > *Click on the Plugins Tab* > *search for Bazel*: the feature's plugins should be in the list
- In the *Project Explorer* view, you should be able to right-click, choose *Import*, and see a *Bazel* menu item.

### Known Issues

If you have a problem, searching the full [Issues list](https://github.com/salesforce/bazel-eclipse/issues)
  is a good thing to do.

### Updating your Bazel Eclipse feature

If you would like a newer build of the Bazel Eclipse feature, you will need to re-install it.
Repeat the above steps using with a newer version.

### Using the Bazel Eclipse Feature

Now that your tool chain is ready, it is time to start using it.
We cover how to use the Bazel Eclipse feature [in our User's Guide](using_the_feature.md).
