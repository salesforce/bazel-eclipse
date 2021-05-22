## Bazel Eclipse for Windows


### Install Path in Preferences

Is there a standard location for Bazel on Windows?
I don't know so make sure to update your Bazel Preference.

CreateProcess Error Code 5 Access Denied

### BAZEL_SH

todo

### Coursier



https://github.com/bazelbuild/rules_jvm_external/issues/464

```
>> ERROR: Error fetching repository: Traceback (most recent call last):
>> 	File "C:/users/coloradolaird/_bazel_coloradolaird/dugk4uzr/external/rules_jvm_external/coursier.bzl", line 290, column 13, in _coursier_fetch_impl
>> 		fail("Unable to run coursier: " + exec_result.stderr)
>> Error in fail: Unable to run coursier: java.io.IOException: ERROR: src/main/native/windows/process.cc(202): CreateProcessW("C:\Users\coloradolaird\Desktop\devtools\jdk\oraclejdk_11_0_11\bin\java" -noverify -jar C:/users/coloradolaird/_bazel_coloradolaird/dugk4uzr/external/maven/coursier): The system cannot find the file specified.
```

Fixes:
- set JAVA_HOME not just in Git Bash, but in env https://mkyong.com/java/how-to-set-java_home-on-windows-10/
- make sure you are using the JDK not the JRE
