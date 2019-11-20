## Bazel Eclipse Feature Dev Guide

These instructions are for developers wishing to make modifications to the Bazel Eclipse Feature.
If you only want to **use** the feature, check out our simpler instructions [over here.](../using_the_feature.md)

### Learning about Eclipse Features and Plugins

Before you throw yourself headlong into the Bazel Eclipse Feature, we recommend you to learn about
  Eclipse features and plugins in general.
Start with something simple before trying to tackle the Bazel Eclipse Feature.

Recommended resources
- [What are Eclipse Plugins, Features, Products, Natures, etc.](https://stackoverflow.com/questions/2692048/what-are-the-differences-between-plug-ins-features-and-products-in-eclipse-rcp)
- [Build a Hello World plugin](http://www.vogella.com/tutorials/EclipsePlugin/article.html)

### Bazel Eclipse Feature Architecture

We have an [architecture](architecture.md) document.
It provides a high level explanation of the implementation of the feature.

### Three Build Systems, One Happy World

We maintain three independent build systems for the feature - Bazel, Eclipse SDK (JDT), and
  Eclipse SDK (Ant).
You will need all of them.

[This document](threebuilds.md) explains this in more detail.

### Installing your Toolchain

It is assumed that you have already installed your Bazel Eclipse Feature toolchain.
See [this installation document](../install.md) for details.

### Install the Eclipse SDK (includes the Plugin Development Environment)

In addition to the installation steps above, you need to install the **Eclipse SDK** which is more
  than just the typical Java IDE download that you normally use.
The SDK includes the [Plugin Development Environment](http://www.eclipse.org/pde/)
The correct version to use is tracked in our build toolchain [here](../../tools/eclipse_jars).

- [Download the Eclipse SDK](http://download.eclipse.org/eclipse/downloads/)

### Create an Eclipse SDK launcher script

At this point you almost certainly have multiple *Eclipse IDE* versions installed on your machine.
- The install guide told you to download and install the latest *Eclipse IDE for Java*.
- Your company may have a standard version of Eclipse to use
- And you just installed the *Eclipse SDK*, which is yet another flavor of the Eclipse IDE.

Therefore you want to be very intentional about how you launch the Eclipse SDK.
Do not rely on your OS finder to launch "Eclipse", as it might not be the PDE one.

As a best practice, write a simple launcher script named *eclipse_sdk.sh*
 (it is [.gitignore](../../.gitignore)'d already)
 in the root directory of this repository on your local machine.
It will look something like:

```
# launch the Eclipse SDK
/Users/mbenioff/tools/eclipse/sdk49/Eclipse.app/Contents/MacOS/eclipse &
```  

### Import the bazel-eclipse projects into the Eclipse SDK

Follow these steps to begin development of the Bazel Eclipse Feature in the Eclipse SDK IDE.

- Launch the Eclipse SDK
- *File* -> *Import* -> *Existing Project*
- Select the *bazel-eclipse* folder as the root directory
- Eclipse should detect that the projects are there, and offer to import projects *Bazel Eclipse Feature*, *plugin-model* and others.
- Click Finish

:fire: if you see errors or warnings at this point, see the [dev issues page](dev_issues.md)  for help.

### Apply the Bazel Eclipse code formatter to your Eclipse SDK IDE

Please keep tab characters out of the source code and follow our code style.
The best way to do this is to use the code formatter.

- Launch your Eclipse SDK IDE
- *Preferences* -> *Java* -> *Code Style* -> *Formatter*
- Apply the [bazel-eclipse-formatter.xml](../../tools/bazel-eclipse-formatter.xml) style

### How to Run and Debug the Feature using the Eclipse SDK

This is a breeze.
The Eclipse community has done a wonderful job making feature development and debug super easy.

Click on *Run* > *Debug Configurations...* menu in the Eclipse SDK instance
   (the one in which you imported the Eclipse plugin projects).
Let's refer to this one as the **outer** Eclipse.
In the Debug Configurations, there should already be an entry *Eclipse Application* > *Runtime Eclipse*.
If not, select *Eclipse App* template in the left nav, give it the name *Inner Eclipse*, then click *Apply*.
Click on it.

On the *Plugins* tab, make sure the Bazel Eclipse plugins are in the list.
You might have to add it.

Click the *Debug* button, you should be off and running.
It will launch a new Eclipse (let's call this the **inner** Eclipse or *runtime* Eclipse) with the Bazel feature installed.
You can set breakpoints in the plugin code as needed.

:fire: If you get an error about *UnsupportedClassVersionError* see the [dev issues](dev_issues.md) page.

### How to Build and Run the Feature Tests on the Bazel Command Line

The Bazel Eclipse Feature uses Bazel as its command line build system.

```
bazel build //...
bazel test //...
```

If you see any errors or warnings, check the [dev issues page](dev_issues.md) for help.

### How to log and configure logging
Logging has been simplified for plugin development and [instructions](logging.md) has been created to address how to log and configure logging levels, etc. It can be found [here](logging.md).

### Precheckin Procedure


1. Run the tests via the Bazel build before submitting a PR, as there is no CI system to catch a mistake. ```bazel test //...```
2. Make sure the feature builds correctly from the Eclipse SDK
3. Run the feature from the Eclipse SDK (*Run* -> *Run As* -> *Inner Eclipse*). Import a Bazel workspace to test it.
4. If you are making big changes, try also to run the feature from a non-SDK install. You will do this by installing the feature updatesite zip file. See the *Testing with a Plain Eclipse Install* section below. Import a Bazel workspace to test it.

### CI

Within Salesforce, we run CI builds against the Git repo.
Due to security limitations, we cannot expose these jobs to the public.

### Releasing the Feature

You will do most of your development and testing from the Eclipse SDK.
But you will want to test the feature from a regular Eclipse from time to time.
Some issues only appear when the feature is installed from the update site.

Eclipse features are released to the public via *update sites*.
Normally these are hosted on a web server somewhere.
But for convenience you can also build and install an update site zip file (aka an Eclipse feature archive).

As covered in our [build system doc](threebuilds.md) we currently use the Eclipse SDK *Export...* for creating the update site for the feature.
