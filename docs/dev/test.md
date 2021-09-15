# Bazel SDK and Bazel Eclipse Test Frameworks

In the bazel-eclipse git repository, there are a few test frameworks provided to help write functional tests instead of integration tests. Functional tests are a challenge because:

- Bazel SDK invokes the Bazel executable to run builds and collect metadata about the workspace.
- Bazel SDK scans the workspace and parses some file types.
- Bazel Eclipse invokes many Eclipse SDK APIs during operation.

Because of these facts, most tests would need to be integration tests. To address this problem, a few frameworks have been built to make it possible to write functional tests.

## Current Technical Debt

Currently, we have some technical debt with these frameworks.

First, they are not well documented. Second, Because the Bazel SDK was originally developed within Bazel Eclipse, many of the Bazel SDK functional tests still live in the Bazel Eclipse project.

## Bazel SDK Test Frameworks

Once again, because of technical debt, when looking for usages of these frameworks be sure to also look in Bazel Eclipse projects. Many of the usages of these frameworks are misplaced in Bazel Eclipse.

### TestBazelWorkspaceFactory

Some features of the SDK scan files on the filesystem, and parse the contents of some of the files. Since the SDK uses raw Java _java.io.File_ operations, we cannot mock this layer. Instead, the **TestBazelWorkspaceFactory** test framework generates a simulated Bazel workspace on the file system.

It creates the workspace in a directory provided by the test, which should be a temporary directory. It then creates files such as:

- WORKSPACE (empty, the SDK just checks for the existence of this file in some cases)
- Directory structure for Bazel packages
- Directory structure for output base
- BUILD files (containing simulated targets like java_library, java_test, etc)
- .java files (containing package name, basic type def)
- Aspect .json files (contains simulated json info for the java targets)

These files have enough information in them to support operations for SDK file scanning. This is mostly used for testing these types of features:

- project import
- classpath computation

The _TestOptions_ class offers a way to customize the created workspace.

The _EclipseFunctionalTestEnvironmentFactory_ class shows examples for BEF functional tests that use this framework.

To explore this class, it can be helpful to pass in a non-temporary directory so you can see what was written after the test finishes. Just make a code change to put your chosen directories into the constructor of _TestBazelWorkspaceDescriptor_.

### MockCommandBuilder

This mock framework is used when you want to test interactions with Bazel executable. It mocks the running of a Bazel command and returns simulated output. There are _Command_ subclasses such as:

- MockBuildCommand - simulates a ‘bazel build’ command
- MockInfoCommand - simulates a ‘bazel info’ command
- MockQueryCommand - simulates a ‘bazel query’ command
- MockTestCommand - simulates a ‘bazel test’ command

These commands are built by the _MockCommandBuilder_, which is a subclass of _CommandBuilder_. Throughout the SDK there are references to _CommandBuilder_. Whenever you are testing code that relies on _CommandBuilder_, you will need to set up the _MockCommandBuilder_.

In some cases, the _MockCommandBuilder_ can create the correct mock response to a command without any help. But in most cases you need to provide the output that will come from the simulated command. If you don’t know when to do this, just try the test and it will throw an exception if it cannot generate the correct output without your help.

Of all the test frameworks, this is probably the hardest to learn and least documented.
It is useful to see how _MockEclipse_ uses _MockCommandBuilder_.


## Bazel Eclipse Test Frameworks

### MockEclipse Framework

Bazel Eclipse plugins assume they are running inside the OSGi environment of the Eclipse IDE. BEF invokes a number of Eclipse APIs, including JDT APIs.

Writing functional tests for code that invokes Eclipse APIs is problematic.
There is a way to run integration tests using a headless Eclipse, but this is not ideal.
Such tests take a long time to run.
It is good to have a few integration tests, but not too many.

Instead, for functional tests there is a _MockEclipse_ framework that simulates all of the Eclipse APIs that BEF requires.

There are a number of example usages of the _MockEclipse_ framework that show how it can be used. Most of them use the _EclipseFunctionalTestEnvironmentFactory_ class to first generate a a test workspace, and then wire up the _MockEclipse_ environment using information from that test workspace.

If you happen to use an Eclipse API that is not yet mocked, it will throw an exception making it clear which API you need to mock.
