# Bazel Eclipse Feature: Launch Configurations

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

Follow these steps:

1. Navigate to *Run As...* and click the *New* button for *Bazel Target*
1. Select the project, and then the test target from the drop down.
1. You should now be able to run the test.

You can also debug it, with breakpoints, by launching as a *Debug Configuration*.

:warning: the standard JUnit Launcher (*Run As JUnit Test*) does not work.
[Issue 18](https://github.com/salesforce/bazel-eclipse/issues/18) tracks that work item.
