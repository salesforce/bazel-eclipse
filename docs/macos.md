## Hints and Tips for Eclipse on Mac

I have used Eclipse for years on Mac, but recently it became problematic for me.
After an OS update, I couldn't launch Eclipse anymore.

If you are getting any of these errors, this page is for you:

- "Failed to create the Java Virtual Machine"
- "You need a Java SE 6 runtime. Would you like to install one now?"
- "JavaVM: Failed to load JVM: /Library/Java/JavaVirtualMachines/XYZ/Contents/Home/bundle/Libraries/libserver.dylib"

This page has a some ideas on how to get around these and similar problems on the Mac.
I am by no means an expert, so feel free to add/update the information if you have it.

## Listing the JDKs on Mac

To list your currently installed system JDKs, do the following:

```bash
/usr/libexec/java_home -V
```

These are the JDKs installed at the system level, and does not include those downloaded independently like
  with BLT.
It will list the chosen **system JDK** at the bottom of the list.
How this JDK is chosen is described below.

The system JDK may not correlate with what you find when you do this in Bash:

```bash
echo $JAVA_HOME
java -version
```

That defines your **Bash JDK**.
When you run *java* from the command line, that is the JDK being used.

## Launching Eclipse

When you launch Eclipse by using the installed AppLauncher, it uses the **system JDK**.
It seems to ignore the **Bash JDK**, and even the *-vm* option that you might manually add to the *eclipse.ini*.
See [this note](https://help.eclipse.org/neon/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fmisc%2Fruntime-options.html) in the official Eclipse docs:

```
The launcher uses the system JavaVM framework and will always load the vm in-process
using the JNI invocation API.
```

This means you have to be equipped to hack around on the system JDK if your Eclipse fails to launch.
The next section explains some tricks for doing that.

## System JDK Hacks

By default, the system JDK will pick up the latest JDK you have installed.
As you troubleshoot, you will need a few ways to manipulate the system JDK.
In my case, I used three different techniques before I was finally able to launch Eclipse.

**Capabilities**

[This article](https://oliverdowling.com.au/2014/03/28/java-se-8-on-mac-os-x/) talks about how you sometimes need to add declared *capabilities* to a JDK.
It requires updating the contents of the *Info.plist* file in the JDK directory.
I did that while trying to sort out my Eclipse launching problem, but I am not sure if it helped.

**Disabling an installed JDK**

You can also prevent an installed JDK from being picked up in the system JDK selection process.
This is done by [renaming the Info.plist file](https://stackoverflow.com/questions/21964709/how-to-set-or-change-the-default-java-jdk-version-on-os-x) in the JDK Contents directory.

**Fixing the missing libserver.dylib**

This was the last problem I faced when launching Eclipse.
I found the answer [in this article](https://oliverdowling.com.au/2014/03/28/java-se-8-on-mac-os-x/).
I had to softlink a file within the JDK.
