## Sync/Updating your Workspace

This page contains information related to workspace synchronization.

### Basics: Sync

The Bazel Eclipse Feature use the information from the BUILD and WORKSPACE file and the project view to create projects in Eclipse.
The feature invokes several Bazel commands as part of this process to query the graph and compute classpath information.
This process is called **sync** (short for synchronization).

If a Bazel command fails during the process the **sync** will be incomplete and fail as well.
You have to fix the Bazel issue and **sync** again.

**What happens during sync?**

A **sync** will execute the `target_discovery_strategy` to discover targets to provision (setup) in Eclipse.
It then calls the `target_provisioning_strategy` to group targets into Eclipse projects.

The following information will be configured during a **sync**:
* projects and which targets they represent
* project settings (copied from workspace project to individual target projects)
* source and resource folders
* JDK configuration (using Bazel's JDK toolchain info) and project compile options
* Eclipse preferences (see `import_preferences`)

After the first processing round is finished a classpath update round will be triggered.


**When to sync?**

Rule of thumb: whenever you modify the project view (`.eclipse/.bazelproject` and any of the `import` files) you have to **sync**.

Only *some* modifications in `BUILD` and `WORKSPACE` files requires Sync.
Most notable changes to the project structure (eg., source or resource folder configurations or new/removed targets).


### How to Sync?

A sync can take very long time.
Therefore BEF and BJLS will never sync automatically.
In order to sync manually use one of the following options.


Using Eclipse ![BEF Logo](../logos/bef_logo_small.png) see [Sync and Build in BEF](../bef/sync_and_build.md)

Using the Bazel Java Language Server ![BJLS Logo](../logos/bjls_logo_small.jpeg) please check the documentation of the IDE/editor plug-in.


### Memory & Performance Tuning

The Sync process processes a lot of Bazel metadata.
The data is read into memory and stored in an internal cache.
The cache size and TTL can be configured to allow tuning of the Sync process.

The general recommendation is to design the the cache size and TTL so that the entire Sync process produces the most cache hits.
A cache miss means that a Bazel query command needs to be executed.
Thus, if you see a lot of query commands for loading/obtaining Bazel package/target information this may be an indication of a cache miss.

The following system properties can be used to tune the cache:
* `eclipse.bazel.model.cache.maximumSize` - the maximum cache size (roughly the sum of number of packages plus targets plus build files to expect during sync);
  default is [100,000,000 entries](https://github.com/search?q=repo%3Asalesforce%2Fbazel-eclipse+getCacheMaximumSize+language%3Ajava&type=code)
* `eclipse.bazel.model.cache.expireAfterAccessSeconds` - how long to retain information in the cache (should be higher than the maximum expected sync time);
  default is [30 minutes](https://github.com/search?q=repo%3Asalesforce%2Fbazel-eclipse+getCacheExpireAfterAccessDuration+language%3Ajava&type=code)

System properties should be declared as additional JVM arguments (eg., `-Declipse.bazel.model.cache.maximumSize=500000` as VM arguments).

### Next Topic: Understanding the Java Classpath

The next page in our guide discusses the [Java classpath](classpath.md).
