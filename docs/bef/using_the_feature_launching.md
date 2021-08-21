# Bazel Eclipse Feature: Launch Configurations ![BEF Logo](../logos/bef_logo_small.png)

Running external processes, tests or apps (Java main methods) is supported using Bazel Launch Configurations.

## Running Java apps from the IDE

The IDE delegates to "bazel run" to run Java main methods.  Therefore your main
method must be backed by a runnable Bazel target type. Currently, only `java_binary`
is supported.

Example:

If you have a Bazel target in your BUILD file that looks like this:
```
java_binary(
    name = "fruit-salad-service",
    main_class = "demo.salesforce.salad.FruitSalad",
    ...
)
```

You can run `FruitSalad`'s main method by creating a launch configuration:

1. Navigate to *Run Configurations...*
1. Create a new instance of the *Bazel Target* launcher
1. Identify the project and *java_binary* target

You should now be able to run the `fruit-salad-service` app.
You can also debug it, with breakpoints, by launching as a *Debug Configuration*.

:warning: the standard Java application launcher (*Run As Java Application*) does not work.

## Running Tests from the IDE

There are several paths to run (or debug) tests from BEF.

**Method 1: top-level Run menu**

1. In the Package Explorer view, click on the test folder (*src/test/java*), a test Java package (*com.salesforce.hello*), or a specific test class (*HelloWorldTest.java*)
1. Navigate to the top-level *Run* menu, choose *Run As* and then *JUnit Test*
1. The JUnit view will populate with the test results

You can also debug it, with breakpoints, by launching it as a *Debug Configuration*.

**Method 2: Package Explorer context menu**

1. In the Package Explorer view, click on the test folder (*src/test/java*), a test Java package (*com.salesforce.hello*), or a specific test class (*HelloWorldTest.java*)
1. Right click on it, and find the *Run As* or *Debug As* item in the context menu
1. Choose *JUnit Test*
1. The JUnit view will populate with the test results

:point_right: The first time you run JUnit tests in your Eclipse workspace, you will be presented
  with a dialog to choose the *Preferred Launcher*.
The **Bazel JUnit Launcher** is the correct one to choose.
You can configure this per launcher, or for most cases click the *Change Workspace Settings* link
  and configure it for all launchers in your Eclipse workspace.
When doing this, click on both *\[Debug\]* and *\[Run\]* types, and check the box for
  *Bazel JUnit Launcher* for each.

### Next Topic: Explore!

This is the end of the step by step user guide.
At this point you have hopefully imported your Bazel workspace and are ready to develop.

Please press the 'star' button at the top of this page if you have not already done so.
This helps us get more time and resources for this project.

If you have problems using BEF, please file an Issue and we will try to help you.
