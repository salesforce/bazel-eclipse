## Installing Eclipse and the Bazel Eclipse Feature

This page documents how to get up and running with Eclipse and the Bazel Eclipse feature.
These instructions are for **users that just want to use** the Bazel Eclipse feature.

 👷There is [a different installation process](dev/dev_guide.md) if you want to actually make changes to the feature.

This section will eventually contain a matrix of supported configurations.
But at this point, we are not that organized.
Install a recent copy of whatever is asked for below, unless a specific version is noted.

### Installing a JDK

At this time, we don't have specific guidance on what JDK version you should be using.
Any JDK 8 or above should work.
We support old and new versions of Java, so whatever you pick should just work.
If you find one that does NOT work, let us know.
More details of our JDK support strategy can be [found here](dev/jdk.md).

### Installing Eclipse

Download the latest release of [Eclipse IDE for Java Developers](https://www.eclipse.org/downloads/packages/release/2018-09/r/eclipse-ide-java-developers).
The feature is built against a [recent version](../tools/eclipse_jars) of the Eclipse SDK, so older versions won't work.

**Launching Eclipse**

This seems like it is a no-brainer.
Eclipse has been around for two decades, so it should launch with no issues.
But it doesn't launch in some cases, at least on Macs.
Consult [this doc](mac_eclipse_jdk.md) if your Eclipse fails to launch on Mac.

### Installing Bazel

The Bazel Eclipse Feature does not come with an embedded install of Bazel.
You must have Bazel installed on your machine and in your shell path.

The [WORKSPACE file](../WORKSPACE) has a version check which is run when building the Bazel Eclipse Feature.
While you can diverge from this version when *using* the Bazel Eclipse Feature, it is a good hint as to what
  Bazel version will work with the Bazel Eclipse Feature.

### Installing the Bazel Eclipse feature into Eclipse

You will first need to obtain an update site archive (.zip file) of the Bazel Eclipse feature,
  which is [covered on this page](releases.md).
Next, follow these steps to install it in your Eclipse IDE.

**Install the Bazel Eclipse feature into Eclipse:**
- Start Eclipse.
- In Eclipse, go to *Help* -> *Install New Software*
- Click the *Add* button
- Click the *Archive* button
- Locate the file *bazel-eclipse-feature.zip* and click *Open*
- Give the location a name, like *Bazel-Eclipse updatesite*
- **Bug workaround:** uncheck the *Group items by category* box, otherwise you won't see the feature in the dialog
- Check the box next to the *Bazel Eclipse Feature* item, then hit *Next*
- Click *Next/Agree/Finish* until it completes.
- Restart Eclipse

**To verify that it is installed:**
- Start Eclipse
- *Eclipse* > *About* > *Installation Details* > *Click on the Plugins Tab* > *search for Bazel*: the feature's plugins should be in the list
- In the *Project Explorer* view, you should be able to right-click, choose *Import*, and see a *Bazel* menu item.

### Updating your Bazel Eclipse feature

If you would like a newer build of the Bazel Eclipse feature, you will need to re-install it.
Repeat the above steps using an archive with a newer version.

### Using the Bazel Eclipse Feature

Now that your tool chain is ready, it is time to start using it.
We cover how to use the Bazel Eclipse feature [in our User's Guide](using_the_feature.md).
