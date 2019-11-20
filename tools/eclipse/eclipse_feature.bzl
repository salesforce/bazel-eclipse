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

load(
    "//tools:verbose.bzl",
    "verbose",
)
load(
    "//tools/eclipse_jars:eclipse_jars.bzl",
    "get_eclipse_jar_list",
    "get_eclipse_declared_deps_for_manifest"
)



def _eclipse_feature_impl(ctx):
  verbose("GENERATE FEATURE ARCHIVE (called from //plugin-core/BUILD)")
  verbose("  _eclipse_feature_impl output location: "+ctx.outputs.out.path)

  feature_xml = ctx.actions.declare_file(ctx.label.name + ".xml")
  ctx.actions.run(
    outputs = [feature_xml],
    inputs = [ctx.file.license],
    executable = ctx.executable._feature_xml_builder,
    arguments = [
      "--output=" + feature_xml.path,
      "--id=" + ctx.label.name,
      "--label=" + ctx.attr.label,
      "--version=" + ctx.attr.version,
      "--provider=" + ctx.attr.provider,
      "--url=" + ctx.attr.url,
      "--description=" + ctx.attr.description,
      "--copyright=" + ctx.attr.copyright,
      "--license_url=" + ctx.attr.license_url,
      "--license=" + ctx.file.license.path] + [
        "--site=%s=%s" % (site, ctx.attr.sites[site])
        for site in ctx.attr.sites] + [
          "--plugin=" + p.basename for p in ctx.files.plugins])
  ctx.actions.run(
      outputs = [ctx.outputs.out],
      inputs = [feature_xml],
      executable = ctx.executable._zipper,
      arguments = ["c",
                   ctx.outputs.out.path,
                   "feature.xml=" + feature_xml.path],
  )
  return struct(
      eclipse_feature=struct(
          file=ctx.outputs.out,
          id=ctx.label.name,
          version=ctx.attr.version,
          plugins=ctx.files.plugins
      )
  )


eclipse_feature = rule(
   implementation=_eclipse_feature_impl,
   attrs = {
       "label": attr.string(mandatory=True),
       "version": attr.string(mandatory=True),
       "provider": attr.string(mandatory=True),
       "description": attr.string(mandatory=True),
       "url": attr.string(mandatory=True),
       "copyright": attr.string(mandatory=True),
       "license_url": attr.string(mandatory=True),
       "license": attr.label(mandatory=True, allow_single_file=True),
       "sites": attr.string_dict(),
       # TODO: restrict what can be passed to the plugins attribute.
       "plugins": attr.label_list(),
       "_zipper": attr.label(default=Label("@bazel_tools//tools/zip:zipper"),
                             executable=True,
                             cfg="host"),
       "_feature_xml_builder": attr.label(default=Label("//tools/eclipse:eclipse_feature_xml_builder"),
                              executable=True,
                              cfg="host"),
    },
    outputs = {"out": "%{name}_%{version}.jar"})
"""Create an eclipse feature jar."""
