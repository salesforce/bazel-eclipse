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


def eclipse_plugin(name, version, bundle_name, activator=None, exports=None,
                   vendor=None, **kwargs):
  """A macro to generate an eclipse plugin (see java_binary)."""
  verbose("GENERATE ECLIPSE PLUGIN (called from //plugin-core/BUILD)")
  verbose("  eclipse_plugin: name: "+name+" ver: "+version+" bundle: "+bundle_name)

  # COMPUTE DEPS
  # standard dep list for all Eclipse plugins
  _ECLIPSE_PLUGIN_DEPS = get_eclipse_jar_list()
  deps = ["//tools/eclipse_jars:%s" % plugin.replace(".", "_")
          for plugin in _ECLIPSE_PLUGIN_DEPS]
  # add additional deps for this particular Eclipse plugin
  if "deps" in kwargs:
    deps = deps + kwargs["deps"]

  # COMPUTE EXTENDED ARGS PASSED BY CALLER
  args = {k: kwargs[k]
          for k in kwargs
          if k not in [
              "deps",
              "classpath_resources",
              "deploy_manifest_lines",
              "resources",
              "visibility",
              "main_class"]}
  visibility = kwargs["visibility"] if "visibility" in kwargs else None

  # GENERATE CONFIG DATA
  # Generate the .api_description to put in the final jar
  verbose("  eclipse_plugin: writing component xml in "+name+"/.api_description")
  native.genrule(
    name = name + ".api_description",
    srcs = [],
    outs = [name + "/.api_description"],
    cmd = """
cat <<EOF >$@
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<component name="%s_%s" version="1.2">
    <plugin id="%s_%s"/>
</component>
EOF
""" % (name, version, name, version))

  # CLASSPATH RESOURCES
  # The classpath_resources are files that are injected into the final plugin jar
  # at the root of the jar structure by the java_binary rule.
      # TODO(dmarting): this add the plugin.xml dependency here, maybe we
      # should move that to the BUILD file to avoid surprise?
  classpath_resources = [
      ":%s/.api_description" % name,
  ] + (kwargs["classpath_resources"] if "classpath_resources" in kwargs else [])

  # RESOURCES
  resources = []
  if "resources" in kwargs:
    resources = resources + kwargs["resources"]

  # MANIFEST
  # These lines are added to the META-INF/MANIFEST.MF file in the generated plugin jar
  # by the java_binary rule
  _MANIFEST_DECLARED_DEPS = get_eclipse_declared_deps_for_manifest()
  deploy_manifest_lines = [
         "Bundle-ManifestVersion: 2",
         "Bundle-Name: " + bundle_name,
         "Bundle-SymbolicName: %s;singleton:=true" % name,
         "Bundle-Version: " + version,
         "Require-Bundle: " + ", ".join(_MANIFEST_DECLARED_DEPS),
         # TODO: Take the java version from java_toolchain.
         "Bundle-RequiredExecutionEnvironment: JavaSE-1.8",
         "Bundle-ActivationPolicy: lazy",
         "Bundle-ClassPath: .",
       ] + \
         (["Bundle-Activator: " + activator] if activator else []) + \
         (["Bundle-Vendor: " + vendor] if vendor else []) + \
         (["Export-Package: " + exports] if exports else []) + \
         (kwargs["deploy_manifest_lines"] if "deploy_manifest_lines" in kwargs else [])

  # TODO
  # figure out what is writing the META-INF/p2.inf file in the plugin jar file
  #     bazel-bin/plugin/com.salesforce.bazel.eclipse.core_0.0.6.qualifier.jar
  # https://wiki.eclipse.org/Equinox/p2/Customizing_Metadata
  verbose("TODO who is writing p2.inf?")


  # CREATE THE PLUGIN JAR
  verbose("  eclipse_plugin: writing the plugin jar file: %s_%s.jar" % (name, version))
  verbose("    eclipse_plugin: plugin classpath resources: "+', '.join(classpath_resources))
  verbose("    eclipse_plugin: plugin manifest: "+'\n'.join(deploy_manifest_lines))
  verbose("    eclipse_plugin: deps: "+', '.join(deps))
  for arg in args:
    verbose("    eclipse_plugin: arg: "+arg+" value: "+",".join(args[arg]))

  # Note that we use java_binary here not java_library. java_binary allows us to provide
  # the deploy_manifest_lines and classpath_resources options. Also, it unjars all
  # deps into the plugin jar so that the jar has all .class files from all upstream deps.
  # https://docs.bazel.build/versions/master/be/java.html#java_binary
  native.java_binary(
    name = name + "-bin",
    main_class = "does.not.exist",
    classpath_resources = classpath_resources,
    resources = resources,
    deploy_manifest_lines = deploy_manifest_lines,
    deps = deps,
    **args)

  # Rename the output to the correct name: com.salesforce.bazel.eclipse.core_XYZ.jar
  native.genrule(
    name = name,
    srcs = [":%s-bin_deploy.jar" % name],
    outs = ["%s_%s.jar" % (name, version)],
    cmd = "cp $< $@",
    output_to_bindir = 1,
    visibility = visibility,
  )
