## Logging in Bazel Eclipse Plugin

### Introduction
Two frameworks are used to log events inside the Bazel Eclipse feature: Eclipse Platform Logging and SLF4J/Logback.
The Eclipse Platform Logging framework sends messages to the Eclipse event log which is viewable from the "Error Log" view.
This is helpful for exceptions and warnings. But, is not the place to put messages that are for information and debugging purposes.
Thus, a log file is used for these events using SLF4J/Logback. The goal was to embrace making logging easy for developers.

A facade is used to wrap both frameworks so that developers don't have to choose and all of the details are taken care of.

### Definitions: Outer Eclipse vs Inner Eclipse
The following definitions are assumed that outer Eclipse is used for development of the plugin and inner Eclipse is the one running the plugin for testing and debugging.
When the plugin is launched, all log events go to the outer Eclipse's console.
Errors and warnings go to the Error Log View in the inner Eclipse.
Finally, all log events go to the SLF4J/Logback file that is discussed below.

### How To
To log, simply create a LogHelper instance with the class that will log to it.
This is just like SLF4J's LoggerFactory.getLog(Class<?>) method.
```java
import com.salesforce.bazel.eclipse.logging.LogHelper;
...
static final LogHelper LOG = LogHelper.log(SuperCoolClassName.class);
```

The instance for LogHelper has five methods for logging.

```java
public void error(String message, Object... args);
public void error(String message, Throwable exception, Object... args);
public void warn(String message, Object... args);
public void info(String message, Object... args);
public void debug(String message, Object... args);
```
These methods can be found [here](../../bzl-java-sdk/src/main/java/com/salesforce/bazel/eclipse/logging/LogHelper.java).

These methods should be familiar to SLF4j users.
The ```message```and ```args``` work just like SLF4J where ```{}``` is used to substitute values in the ```args```.
The following table determines where the log event goes based on the method.

| Method                                   | SLF4J log | Eclipse error log |
|------------------------------------------|-----------|-------------------|
| ```error(String,Object...)```            | Yes       | Yes               |
| ```error(String,Throwable,Object...)```  | Yes       | Yes               |
| ```warn(String,Object...)```             | Yes       | Yes               |
| ```info(String,Object...)```             | Yes       | No                |
| ```debug(String,Object...)```            | Yes       | No                |

All logs go to the SLF4J log and only ```error``` and ```warn``` events go to the Eclipse error event log.

### Examples

#### Error
```java
try {
    runnable.run(monitor);
} catch (InvocationTargetException | InterruptedException e) {
  LOG.error(e.getMessage(), e);
}
```

#### Warn
```java
LOG.warn("Failed to load: {}", file.getAbsolutePath());
```

#### Info
```java
LOG.info("Rule {} was executed with status {}", ruleName, status);
```

#### Debug
```java
LOG.debug("Health check was successful");
```

### Log Configuration
The SLF4J/Logback log can be configured to change format, log rolling, log size, etc.
To learn more about configuration, please read the [Logback documentation](https://logback.qos.ch/manual/configuration.html).
The configuration file is located in [logback.xml](../../plugin-core/logback.xml).

#### Enable debug events to be seen
If you want to turn on debug level events in the SLF4J/Loback log, change the following xml in the [logback.xml](../../plugin-core/logback.xml) from INFO to DEBUG.
```xml
<root level="INFO">
```
to
```xml
<root level="DEBUG">
```
#### Enable/disable levels for certain classes
If you only want to turn on debug level for a certain class, you can add the following with a fully qualified class name and the minimum level desired.
```xml
 <logger name="org.eclipse.jetty.server" level="DEBUG" />
```
### SLF4J/Logback Log Location
The location of the SLF4j/Logback file is logged in the Eclipse error log so it can be easily found.
It can be found inside the workspace at this location: ```$WORKSPACE/.metadata/.plugins/org.eclipse.core.resources/com_salesforce_bazel_eclipse_core.log```.
