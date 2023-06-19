# Project Views

[Project Views (*.bazelproject files)](https://ij.bazel.build/docs/project-views.html "Open IJ PLug-in Documentation about Project View")  are used to describe what will be made available in your IDE.


## Supported Features

### `directories`

*Eclipse:* Configures which directories are made visible in the workspace project.
See JavaDoc of [BazelProject](../../bundles/com.salesforce.bazel.eclipse.core/src/com/salesforce/bazel/eclipse/core/model/BazelProject.java) for details about project types.

### `targets`


### `derive_targets_from_directories`

### `workspace_type`

Only `java` is supported.

### `java_language_level`

Ignored and inferred from current/workspace `JavaToolchainInfo`.

## Additional Features

The following additional entries are supported to further customize the import experience.

### `target_discovery_strategy`

Customize how targets are discovered when `derive_targets_from_directories` is set to `true`.
Does nothing when `derive_targets_from_directories` is not set or set to `false`.

The default value is `default`, which maps to `bazel-query` and will use `bazel query` to discover targets.
See [BazelQueryTargetDiscovery.java](../../bundles/com.salesforce.bazel.eclipse.core/src/com/salesforce/bazel/eclipse/core/model/discovery/BazelQueryTargetDiscovery.java) to read more about default discovery behavior.

It's possible to add custom strategies via an extension point.

### `target_provisioning_strategy`

Customize how targets are imported/resolved.

The default value is `default`, which maps to `project-per-target` and will resolve each target as an individual project.

#### `project-per-target`

This will import each discovered or explicitly listed target as a single project.
Each project will have its own classpath.
It supports `java_library`, `java_binary` and `java_import` rules and uses the IJ plug-in aspects with `bazel build` to read/extract the information from Bazel.

For details please read the JavaDoc (and Java code) of [ProjectPerTargetProvisioningStrategy.java](../../bundles/com.salesforce.bazel.eclipse.core/src/com/salesforce/bazel/eclipse/core/model/discovery/ProjectPerTargetProvisioningStrategy.java).

##### Pro

* This strategy is closest to how Bazel builds the code.
* It allows fine grained visibility control of imports and exports.
* It supports split Java packages, i.e. Java packages with individual `.java` files grouped into separate targets.
* Bazel is the source of truth, any IDE configuration is ignored.

##### Con

* It may result into an excessive amount of projects, which will slow down Eclipse.
* It requires fine grained configuration in `BUILD´ files.
* Split Java packages are confusing and should be avoided.
  It's better to split your code into separate Java packages and work with api/internal/impl conventions instead of Java's package visibility.
* It requires running `bazel build` to detect classpath configuration.
* Projects are created in a separate `.eclipse` directory and links are used to resolve source files (or directories).
  Thus, in some configurations creation of new Java classes cannot be easily supported from within the IDE.
** Sharing of project metadata becomes more problematic in this setup.

#### `project-per-package`

This will group targets from one package together and import as a single project.
Each project will have a shared classpath for all targets in the package.
It supports `java_library`, `java_binary` and `java_import` rules and uses the IJ plug-in aspects with `bazel build` to read/extract the information from Bazel.

**Note:** Bazel packages mapping *one-to-one with* a Java packages are not recommended with this strategy. Instead this strategy is better suited for Maven-style projects, where a Bazel Java target uses a `glob` pattern for selecting an entire source folder (eg., `glob(["src/main/java/*.java"])`).

For details please read the JavaDoc (and Java code) of [ProjectPerPackageProvisioningStrategy.java](../../bundles/com.salesforce.bazel.eclipse.core/src/com/salesforce/bazel/eclipse/core/model/discovery/ProjectPerPackageProvisioningStrategy.java).

##### Pro

* Easier setup in IDEs, which feels less awkward/special in some Bazel workspaces.
* Suited for Maven-style projects, where a Bazel Java target uses a `glob` pattern for selecting an entire source folder (eg., `glob(["src/main/java/*.java"])`).
* Projects are created directly in the workspace (`.project` and `.classpath` files and `.settings` folder)
** Sharing of project metadata vis SCM is easier
** IDE operations (like creating new classes) should work as usual.
* Bazel is the source of truth, any IDE configuration is ignored.

##### Con

* Split Java packages are not supported. Everything is merged into a single source folder.
* Bazel packages mapping *one-to-one with* a Java packages are not recommended with this strategy.
* It requires running `bazel build` to detect classpath configuration.
* Is not fully implemented and required help/work/contributions.


## Unsupported Features / Limitations

All deprecated items are not supported.

In addition to those, the following items are not supported as well:
* `shard_sync` and `target_shard_size`
* `exclude_library`
* `build_flags`,  `sync_flags` and `test_flags` (use local `.bazelrc`)
* `import_run_configurations`
* `bazel_binary`
* `exclude_target`
* `import_target_output`
* `ts_config_rules`
* `android_sdk_platform` and `android_min_sdk` (`android_*`)
* `test_sources`


