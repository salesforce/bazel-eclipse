## Bazel Java SDK

Under the covers BEF uses the separately managed [Bazel Java SDK](https://github.com/salesforce/bazel-java-sdk)
   for all interactions with Bazel.
However, primary development for the SDK occurs in **this** repository for convenience reasons.

However, the IDE work happening in this repository is largely engaged with making improvements and
  fixes in the SDK.

For that reason, a vendored (static copy) version of the SDK lives in this repository.
As changes are made to the SDK in either repository, the changes are copied across
  using scripts such as [sdk_copybara.sh](../../sdk-copybara.sh).

More details can be found in these places:
- [Bazel Java SDK](https://github.com/salesforce/bazel-java-sdk)
- [Discussion between bazel-eclipse and bazel-ls projects](https://github.com/salesforce/bazel-eclipse/issues/237)
