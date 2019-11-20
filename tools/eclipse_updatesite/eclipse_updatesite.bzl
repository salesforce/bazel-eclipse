# Copyright 2017 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# NOTE
# We are not using Bazel to build the feature updatesite any more, and likely never will.
# See the //docs/dev/threebuilds.md file for an explanation.
#


load(
    "//tools:verbose.bzl",
    "verbose",
)
load(
    "//tools/eclipse_updatesite:eclipse_platform.bzl",
    "eclipse_platform"
)
load(
    "//tools/eclipse_jars:eclipse_jars.bzl",
    "get_eclipse_jar_list",
    "get_eclipse_declared_deps_for_manifest"
)


def _eclipse_p2updatesite_impl(ctx):
  verbose("GENERATE UPDATE SITE (called from //plugin-core/BUILD)")

  print("Generating the Eclipse feature zip distribution. But see docs/dev/threebuilds.md. This distribution must be post-processed before it can be used.")

  feat_files = [f.eclipse_feature.file for f in ctx.attr.eclipse_features]
  args = [
    "--output=" + ctx.outputs.out.path,
    "--java=" + ctx.executable._java.path,
    "--eclipse_launcher=" + ctx.file._eclipse_launcher.path,
    "--name=" + ctx.attr.label,
    "--url=" + ctx.attr.url,
    "--description=" + ctx.attr.description]

  _plugins = {}
  for f in ctx.attr.eclipse_features:
    args.append("--feature=" + f.eclipse_feature.file.path)
    args.append("--feature_id=" + f.eclipse_feature.id)
    args.append("--feature_version=" + f.eclipse_feature.version)
    for p in f.eclipse_feature.plugins:
      if p.path not in _plugins:
        _plugins[p.path] = p
  plugins = [_plugins[p] for p in _plugins]

  ctx.actions.run(
      outputs=[ctx.outputs.out],
      inputs=[
          ctx.file._eclipse_launcher,
          ] + ctx.files._jdk + ctx.files._eclipse_platform + feat_files + plugins,
      tools = [ctx.executable._java],
      executable = ctx.executable._site_builder,
      arguments = args + ["--bundle=" + p.path for p in plugins])


eclipse_p2updatesite = rule(
   implementation=_eclipse_p2updatesite_impl,
   attrs = {
       "label": attr.string(mandatory=True),
       "description": attr.string(mandatory=True),
       "url": attr.string(mandatory=True),
       "eclipse_features": attr.label_list(providers=["eclipse_feature"]),
       "_site_builder": attr.label(
           default=Label("//tools/eclipse:site_builder"),
           executable=True,
           cfg="host"),
       "_zipper": attr.label(
           default=Label("@bazel_tools//tools/zip:zipper"),
           executable=True,
           cfg="host"),
        "_java": attr.label(
           default=Label("@bazel_tools//tools/jdk:java"),
           executable=True,
           cfg="host"),
        "_jdk": attr.label(default=Label("@bazel_tools//tools/jdk:jdk")),
        "_eclipse_launcher": attr.label(
            default=Label("@org_eclipse_equinox//:launcher"),
            allow_single_file=True),
        "_eclipse_platform": attr.label(default=Label("@org_eclipse_equinox//:platform")),
    },
    outputs = {"out": "%{name}.zip"})
"""Create an eclipse p2update site inside a ZIP file."""
