## Bazel Eclipse Feature for Windows ![BEF Logo](logos/bef_logo_small.png)

Windows support has been introduced into the code line as of June 2021 and the
  first release with Windows support is [1.4.0](https://github.com/salesforce/bazel-eclipse/releases).
Please be aware though that the authors of BEF are not well suited to support Windows.

Our support for Windows consists of the following:
- Explicit type model for all styles of paths (filesystem, bazel labels) to enforce separator correctness.
- Windows build in CI to detect test failures on the Windows platform.
- Cursory manual testing on a Windows machine each release.

Speaking personally (@plaird), I did the Windows port but I haven't used Windows
  for development in 12 years.
If you have issues or suggestions on how better to support this platform we would
  welcome any feedback and expertise.

### Prerequisites

Before you continue with this page, we need to assume some things:

- Your Bazel workspace is building correctly on your Windows machine prior to importing into BEF
  - This includes having awareness and solutions for *BAZEL_SH* and *JAVA_HOME* issues
  - Please seek out the right solutions from the [Bazel Windows documentation](https://docs.bazel.build/versions/main/windows.html)
- Understand that BEF is currently limited to Bazel packages with Java rules and [conform to certain file layout conventions](conforming_java_packages.md)
- You are on modern-ish versions of Windows, Bazel, Java, and Eclipse.

If you are good with the above, please follow [our install doc](install.md)
  and our [user guide](using_the_feature.md).

### You MUST Set the Bazel Install Path in Preferences

Is there a standard location for Bazel on Windows?
On Linux and Mac, we have a reasonable default of ```~/bin/bazel``` but on Windows
  it is not clear what the default should be.

Make sure to set the Eclipse preference **prior** to doing any BEF operation (like Project Import).
- Launch Eclipse with BEF installed
- Open _Windows -> Preferences_
- Click on the _Bazel_ section
- Set the Bazel executable path, and set it to your path to the *bazel.exe*

**CreateProcess Error Code 5 Access Denied**

If you get the above error, this is a problem with BEF not finding your Bazel executable.
Make sure you set your Bazel executable preference correctly.
Restart Eclipse if the setting does not appear to take effect.

### Build Failures?

As stated above, your Bazel workspace must be building successfully from the command line before using BEF.
If you have build errors, please investigate general Windows support for Bazel,
  and the role of *BAZEL_SH* in your environment.
Under the covers, BEF is invoking *bazel.exe* from a shell to run your build.

Suggested reading:
- [Bazel for Windows user docs](https://docs.bazel.build/versions/main/windows.html)
- [Bazel for Windows dev docs](https://docs.google.com/document/d/17YIqUdffxpwcKP-0whHM6TFELN8VohTpjiiEIbbRfts)
- [Example Bazel Issue](https://github.com/bazelbuild/bazel/issues/6474)
- [Example StackOverflow Post](https://stackoverflow.com/questions/46181672/windows-10-bazel-sh-configuration)

### Coursier: Error fetching repository

Many Bazel workspaces with Java use *rules_jvm_external* to manage external dependencies.
Under the hood, *rules_jvm_external* uses a tool called Coursier to download the jars.
You may see an error such as this:

```
>> ERROR: Error fetching repository: Traceback (most recent call last):
>> 	File "C:/users/coloradolaird/_bazel_coloradolaird/dugk4uzr/external/rules_jvm_external/coursier.bzl", line 290, column 13, in _coursier_fetch_impl
>> 		fail("Unable to run coursier: " + exec_result.stderr)
>> Error in fail: Unable to run coursier: java.io.IOException: ERROR: src/main/native/windows/process.cc(202): CreateProcessW("C:\Users\coloradolaird\Desktop\devtools\jdk\oraclejdk_11_0_11\bin\java" -noverify -jar C:/users/coloradolaird/_bazel_coloradolaird/dugk4uzr/external/maven/coursier): The system cannot find the file specified.
```

This is covered in [this issue](https://github.com/bazelbuild/rules_jvm_external/issues/464) in *rules_jvm_external*.

Possible fixes:
- Set *JAVA_HOME* not just in Git Bash, but in the Windows environment https://mkyong.com/java/how-to-set-java_home-on-windows-10/
- Make sure you are using the JDK not the JRE
