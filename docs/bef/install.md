## Installing Eclipse and the Bazel Eclipse Feature

This page documents how to get up and running with Eclipse and the Bazel Eclipse feature.
These instructions are for **users that just want to use** the Bazel Eclipse feature.

 👷There is [a different installation process](../dev/dev_guide.md) if you want to actually make changes to the feature.

This section will eventually contain a matrix of supported configurations.
But at this point, we are not that organized.
Install a recent copy of whatever is asked for below, unless a specific version is noted.

### Supported Platforms

We will support you on:
- Mac - reasonably recent OS versions, with issues [noted on this page](macos.md).
- Linux - reasonably recent OS version of a major distro (Ubuntu, Mint, etc)
- Windows - supported, but [we are looking for expertise](windows.md) to support this better.

Other platforms (yo, OS/2 user!) may also work, but you may need to contribute patches if there are issues.

### Installing a JDK

At this time, we don't have specific guidance on what JDK version you should be using.
Any JDK11 or above should work.
We support modern versions of Java, so whatever you pick should just work.
If you find one that does NOT work, let us know.
More details of our JDK support strategy can be [found here](../dev/jdk.md).

### Installing Eclipse

Download the latest release of [Eclipse IDE for Java Developers](https://www.eclipse.org/downloads/packages/release/2018-09/r/eclipse-ide-java-developers).
The feature is built against a of the Eclipse SDK, so older versions won't work.

**Launching Eclipse**

This seems like it is a no-brainer.
Eclipse has been around for two decades, so it should launch with no issues.
But it doesn't launch in some cases, at least on Macs.
Consult [this doc](macos.md) if your Eclipse fails to launch on Mac.

### Installing Bazel

The Bazel Eclipse Feature does not come with an embedded install of Bazel.
You must have Bazel installed on your machine and in your shell path.

The BazelVersionChecker class has a version check when setting the Bazel executable in the Eclipse preferences.
Choose a version of Bazel that is at least as high as that check.

### Installing the Bazel Eclipse feature into Eclipse

**Quick Installation**

Drag the _Install_ button and drop on your running Eclipse IDE, and search for _Bazel_.
It is that easy!

<a href="http://marketplace.eclipse.org/marketplace-client-intro?mpc_install=5403450" class="drag" title="Drag to your running Eclipse workspace.">
  <img style="width:80px;" typeof="foaf:Image" class="img-responsive" src="https://marketplace.eclipse.org/sites/all/themes/solstice/public/images/marketplace/btn-install.svg" alt="Drag to your running Eclipse workspace." />
</a>

**Manually Install the Bazel Eclipse feature into Eclipse:**

First, understand what releases of BEF are available, which is [covered on the Releases page](releases.md).
If you want to install the latest version of BEF, follow these steps to install it in your Eclipse IDE using the update site:

- Start Eclipse.
- In Eclipse, go to *Help* -> *Install New Software*
- Click the *Add* button
<!-- markdown-link-check-disable-next-line -->
- For the location, enter: **https://opensource.salesforce.com/bazel-eclipse/update-site**
- Give the location a name, like *Bazel-Eclipse updatesite*
- Check the box next to the *Bazel Eclipse* item, then hit *Next*
- Click *Next/Agree/Finish* until it completes.
- Restart Eclipse

Otherwise, you can install older versions of BEF using the archive zip files that are listed [on the Releases page](releases.md).

**To verify that it is installed:**
- Start Eclipse
- *Eclipse* > *About* > *Installation Details* > *Click on the Plugins Tab* > *search for Bazel*: the feature's plugins should be in the list
- In the *Project Explorer* view, you should be able to right-click, choose *Import*, and see a *Bazel* menu item.

### Known Issues

Now is a good time to browse the [known issues](issues.md) page which covers
  some common problems.
If you have a problem, searching the full [Issues list](https://github.com/salesforce/bazel-eclipse/issues)
  is also a good thing to do.

### Updating your Bazel Eclipse feature

If you would like a newer build of the Bazel Eclipse feature, you will need to re-install it.
Repeat the above steps using with a newer version.

### Using the Bazel Eclipse Feature

Now that your tool chain is ready, it is time to start using it.
We cover how to use the Bazel Eclipse feature [in our User's Guide](using_the_feature.md).
