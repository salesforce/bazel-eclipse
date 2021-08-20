## Bazel JDT LS Eclipse Feature

This is the logical container of all the plugins related to the Bazel integration with Eclipse.
In Eclipse, such a container is known as a *feature*.
See the [architecture document](../docs/dev/architecture.md) for more information about features.

### Static Files

This packages contains a number of static files that are used by the Eclipse SDK to manage the feature.

- [feature.xml](feature.xml)
- [build.properties](build.properties)
- [MANIFEST.MF](META-INF/MANIFEST.MF)

As you develop the Bazel Eclipse Feature, you may need to update these files.
Make sure to commit them back to the Git repo.

### Bazel Build

We currently do not use the Feature artifacts built by the [Bazel build](BUILD).
Instead, we are relying on the Eclipse SDK to build and export the feature.
We might use Bazel to build the feature artifacts in the future, however.

We are also not using Bazel to build the feature *updatesite*, and likely never will.
There are Bazel rules [in the repo](../tools/eclipse_updatesite) for doing this, but we have no plans to revive it.

See [this doc](../docs/dev/threebuilds.md) file for an explanation.
