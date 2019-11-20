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

You should now be able to run the `fruit-salad-service`  app.
You can also debug it, with breakpoints, by launching as a *Debug Configuration*.

:warning: Do not use the Java Application Launcher - this will not work
because it does not run Bazel.

## Running Tests from the IDE

Follow these steps:

1. Navigate to *Run As...* and click on the *Bazel Target* option
1. Alternatively, you can use the shortcuts to run/debug Bazel targets
For Mac:
```
Bazel Target run: COMMAND+ALT+X, B
Bazel Target debug: CTRL+COMMAND+ALT+D, B
```
For Linux:
```
Bazel Target run: CTRL+ALT+X, B
Bazel Target Debug: SHIFT+CTRL+ALT+D, B
```

You should now be able to run the test.
You can also debug it, with breakpoints, by launching as a *Debug Configuration*.

:warning: Do not use the JUnit Launcher. If you have used Eclipse for a long time, you have the muscle memory to "Run as Junit Test" - this will not work
because it does not run Bazel.
