# Project Views

[Project Views (*.bazelproject files)](https://ij.bazel.build/docs/project-views.html "Open IJ PLug-in Documentation about Project View")  are used to describe what will be made available in your IDE.


## How it works

When importing a Bazel workspace the Bazel Eclipse Feature (and the Bazel JDT Language Server) will create a `.eclipse/.bazelproject` file within the Bazel workspace.
The file will be used to drive the import/synchronization of Bazel directories and targets into Java projects with their own classpath.
This resolution can be customized using `target_discovery_strategy` and `target_provisioning_strategy` (see below for details).

Note, an existing `.eclipse/.bazelproject` file will never be overridden.
It will be used as is.

**Import and Share**

Instead of using only the `.eclipse/.bazelproject` file we recommend using [`import`](#import).
Teams should share recommended `.bazelproject` files in source control (eg. Git).
Simply add an `import` statement for any `.bazelproject` file you want to use.

The `.eclipse/.bazelproject` file should not be shared in source control.
It should be added to `.gitignore` instead (like most of the stuff within the `.eclipse` folder).
The `.eclipse` folder may also be used to create virtual Java projects for Bazel targets.
This is required to support individual classpath scopes per target.
See `project-per-target` strategy for more details.

**tools/eclipse/.managed-defaults.bazelproject**

Some of the additional settings below may cause issues in IntelliJ.
Therefore the Bazel Eclipse Feature (and the Bazel JDT Language Server) support importing a file with default settings just for Eclipse, VS Code or other tools using the language server.
Simply create a file `tools/eclipse/.managed-defaults.bazelproject` and put additional settings like `target_discovery_strategy` and `target_provisioning_strategy` only in this file, which can be added to source control and shared with every team member.
The import process will check for the existence of this file and add an import line to `.eclipse/.bazelproject`.

**project-per-package**

If you are looking for inspiration try this `target_provisioning_strategy`.
It creates a more *traditional* IDE experience.


## Supported Standard Features

### [`import`](https://ij.bazel.build/docs/project-views.html#import)

### [`directories`](https://ij.bazel.build/docs/project-views.html#directories)

*Eclipse:* Configures which directories are made visible in the workspace project.
See JavaDoc of [BazelProject](../../bundles/com.salesforce.bazel.eclipse.core/src/com/salesforce/bazel/eclipse/core/model/BazelProject.java) for details about project types.

### [`targets`](https://ij.bazel.build/docs/project-views.html#targets)

### [`derive_targets_from_directories`](https://ij.bazel.build/docs/project-views.html#derive_targets_from_directories)

### [`workspace_type`](https://ij.bazel.build/docs/project-views.html#workspace_type)

Only `java` is supported.

### [`java_language_level`](https://ij.bazel.build/docs/project-views.html#java_language_level)

Ignored and queried from current/workspace `JavaToolchainInfo`.

### [`build_flags`](https://ij.bazel.build/docs/project-views.html#build_flags), [`sync_flags`](https://ij.bazel.build/docs/project-views.html#sync_flags) and [`test_flags`](https://ij.bazel.build/docs/project-views.html#test_flags)

### [`shard_sync`](https://ij.bazel.build/docs/project-views.html#shard_sync) and [`target_shard_size`](https://ij.bazel.build/docs/project-views.html#target_shard_size)


## Additional Features

The following additional entries are supported to further customize the import experience.

### `target_discovery_strategy`

Customize how targets are discovered when `derive_targets_from_directories` is set to `true`.
Does nothing when `derive_targets_from_directories` is not set or set to `false`.

The default value is `default`, which maps to `bazel-query` and will use `bazel query` to discover targets.
It's possible to add custom strategies via an extension point.

#### `bazel query`

This performs a `bazel query` to obtain all list of all `BUILD` files.
The list is then processed and directory information is translated into a list of Bazel packages.
The list of packages is further refined with the project view information.
Directories without an anchor in the `directories` list will be discarded.
Directories with an anchor explicitely excluded will also be discarded.
The remaining list of packages is queried again using `bazel query` to obtain all possible targets.

See [BazelQueryTargetDiscovery.java](../../bundles/com.salesforce.bazel.eclipse.core/src/com/salesforce/bazel/eclipse/core/model/discovery/BazelQueryTargetDiscovery.java) to read more about default discovery behavior.

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
* Bazel is the source of truth, any IDE configuration/convenience is ignored.

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
* The root package (`//`) cannot be imported as project.
* Overlapping projects (nested packages) cause issues and will be ignored.
* It requires running `bazel build` to detect classpath configuration.
* Is not fully implemented and required help/work/contributions.

### `target_provisioning_settings`

A list of settings to further tweak the `target_provisioning_strategy`.
The syntax of each entry is `key=value`, where `key` and `value` are expected string values (note the absence of whitespace characters).

* `jre_system_library` (possible values: `current_java_runtime` and `execution_environment`, configures the JRE System Library classpath container to use either the Bazel workspace's Java runtime or an execution environment id)
* `project_name_separator_char` (used by `project-per-package` to replace `/` in package path for project name)
* `java_like_rules` (used by `project-per-package`, comma separated list of additional java like rules to analyze when collecting targets for each package)

### `project_mappings`

A list of mappings from targets (typically from external repositories) to projects in the IDE.
This should be used in combination with `--override_repository`.
The syntax of each entry is `target=projecturi` (no space around equals sign), where `target` is typically an external repository (eg., `@myjar`) and `projecturi` the project type and name (eg., `project:my-jar`).
Currently only the scheme `project:` is supported, which will map to an existing Eclipse/Language Server project with the given name.

Out of the box the language server as well as the Eclipse feature resolves references to other Bazel workspaces using the workspace name.
Thus, if the external workspace is already imported and its name matches its projects will be used when resolving dependencies.
This is extremely useful in combination with `local_repository` references.
In case the name does not match an explicit `project_mappings` should be setup instead.

The lookup within the IDE implements a simple logic to make defining the mappings less verbose.
A mapping for target `@some_external_target//:some_external_target` can be defined using just `@some_external_target`.

### `import_preferences`

A list of preferences files (`*.epf`) to import into Eclipse during sync.

### `project_settings`

A list of project settings files (`*.prefs`) to copy into each provisioned Eclipse project during sync.

The settings files must follow the naming conventions of Eclipse project settings (eg., `org.eclipse.jdt.core.prefs`).
Only the file name part without the `*.prefs` extension will be used for determin the target project settings file.
The folder will be ignored.

### `discover_all_external_and_workspace_jars`

A boolean flag to discover external and workspace jars for making them available on the workspace project classpath (default is `false`).

When this flag is set to `true`, an attempt will be made to discover all jars in the workspace and from external repositories to make them available to the IDE.
This will increase the sync time but allows to use discover types for everything in the workspace.
As an additional benefit, refactorings in the IDE might become less risky.
It allows IDE to discover and find binary references, i.e. references by/from code not in the current project view.
This will trigger a pop-up or some other warnings so that you will have a chance discovering early that a refactoring will break the Bazel workspace.

Note, it's recommended to add the `--output_groups=+_source_jars` when building to force Bazel to always build sources.
This can be done with the following in your `.bazelrc` file:
```
# For having sources available in the IDE always create source jars
build --output_groups=+_source_jars
```

### `external_jars_discovery_filters`

When `discover_all_external_and_workspace_jars` is set to `true` this can be set to apply additional filtering to the discovered external repository names.

The value is a list. Wildcard globbing can be used for matching repository names as well as `-` prefix for exclusions.

###  `test_sources`

A list of globs with directories to flag as containing test sources.

```
test_sources:
  **/test/unit
```

This attribute also support exclusions if they begin with `-`.

If the glob matches a package which is provisioned with the `project-per-package` strategy, all source folders in the project will be marked as containing test sources.


## Unsupported Features / Limitations

All deprecated items are not supported.

In addition to those, the following items are not supported as well:
* `exclude_library`
* `import_run_configurations`
* `exclude_target`
* `import_target_output`
* `ts_config_rules`
* `android_sdk_platform` and `android_min_sdk` (`android_*`)


