## Bazel Java SDK

Under the covers BEF uses the separately managed [Bazel Java SDK](https://github.com/salesforce/bazel-java-sdk)
   for all interactions with Bazel.
Primary development for the SDK should occur in that repository.

However, the IDE work happening in this repository is largely engaged with making improvements and
  fixes in the SDK.
Roughly 90% of the code to support Bazel in Eclipse is not specific to Eclipse, and therefore lives
  in the SDK.

For that reason, a vendored (static copy) version of the SDK lives in this repository.
As changes are made to the SDK in either repository, the changes are copied across
  using scripts such as [sdk_copybara.sh](../../sdk_copybara.sh).

More details can be found in these places:
- [Bazel Java SDK](https://github.com/salesforce/bazel-java-sdk)
- [Discussion between bazel-eclipse and bazel-ls projects](https://github.com/salesforce/bazel-eclipse/issues/237)
