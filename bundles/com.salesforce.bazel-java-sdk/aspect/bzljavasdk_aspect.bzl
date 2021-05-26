# Copyright (c) 2019-2021, Salesforce.com, Inc.
# Copyright 2016 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


# Aspect for Bazel Java SDK, taken from an early version of intellij_info.bzl
# TODO upgrade this to their latest work

DEPENDENCY_ATTRIBUTES = [
  "deps",
  "runtime_deps",
  "exports",
]

def struct_omit_none(**kwargs):
    d = {name: kwargs[name] for name in kwargs if kwargs[name] != None}
    return struct(**d)

def artifact_location_or_none(file):
  return None if file == None else file.path

def library_artifact(target, java_output):
  if java_output == None:
    return None
  if java_output.class_jar == None:
    return None

  return struct_omit_none(
        jar = artifact_location_or_none(java_output.class_jar),
        interface_jar = artifact_location_or_none(java_output.ijar),
        source_jar = artifact_location_or_none(java_output.source_jar),
  )

def library_artifact_onlyclasses(target, class_jar):
  if class_jar == None:
    return None
  return struct_omit_none(
        jar = artifact_location_or_none(class_jar),
  )

def annotation_processing_jars(annotation_processing):
  return struct_omit_none(
        jar = artifact_location_or_none(annotation_processing.class_jar),
        source_jar = artifact_location_or_none(annotation_processing.source_jar),
  )

def jars_from_output(output):
  """ Collect jars for classpath_jars from Java output.
  """
  if output == None:
    return []
  return [jar
          for jar in [output.class_jar, output.ijar, output.source_jar]
          if jar != None and not jar.is_source]

def java_rule_ide_info(target, ctx):
  if hasattr(ctx.rule.attr, "srcs"):
     sources = [artifact_location_or_none(file)
                for src in ctx.rule.attr.srcs
                for file in src.files.to_list()]
  else:
     sources = []

  jars = []
  classpath_jars = depset()
  if target[JavaInfo].outputs.jars != None and len(target[JavaInfo].outputs.jars) > 0:
    # standard Java library or test rule
    jars = [library_artifact(target, output) for output in target[JavaInfo].outputs.jars]
    classpath_jars = depset([jar
       for output in target[JavaInfo].outputs.jars
       for jar in jars_from_output(output)])
  else:
    # proto-java library rules end up in here, no jars listed on the output object, so we resort to using transitive_runtime_deps
    #print("No output jars for "+target.label.name+", resorting to use transitive_runtime_deps")

    if target[JavaInfo].transitive_runtime_deps != None:
      # transitive_runtime_deps is a depset of File objects
      #print("Adding class jars via transitive_runtime_deps for "+target.label.name)
      jars = [library_artifact_onlyclasses(target, output) for output in target[JavaInfo].transitive_runtime_deps.to_list()]
      classpath_jars = depset([jar for jar in target[JavaInfo].transitive_runtime_deps.to_list()])

  gen_jars = []
  if target[JavaInfo].annotation_processing and target[JavaInfo].annotation_processing.enabled:
    gen_jars = [annotation_processing_jars(target[JavaInfo].annotation_processing)]
    classpath_jars =  depset([ jar
        for jar in [target[JavaInfo].annotation_processing.class_jar,
                    target[JavaInfo].annotation_processing.source_jar]
        if jar != None and not jar.is_source], transitive = [classpath_jars])

  if hasattr(ctx.rule.attr, "main_class"):
      main_class = ctx.rule.attr.main_class
  else:
      main_class = None

  return (struct_omit_none(
                 sources = sources,
                 main_class = main_class,
                 jars = jars,
                 generated_jars = gen_jars
          ),
          classpath_jars)


def _aspect_impl(target, ctx):
  # e.g. java_library
  rule_kind = ctx.rule.kind
  rule_attrs = ctx.rule.attr

  json_files = []
  classpath_jars = depset()
  all_deps = []

  #print("Aspect Target: "+target.label.name)
  #print(dir(target))
  #print(target)
  #print("  JavaInfo for "+target.label.name)
  #print(target[JavaInfo])
  #print("  RuleAttrs "+target.label.name)
  #print(rule_attrs)

  hasDepAttr = False

  # "deps", "runtime_deps", "exports"
  for attr_name in DEPENDENCY_ATTRIBUTES:
    if hasattr(rule_attrs, attr_name):
      deps = getattr(rule_attrs, attr_name)
      if type(deps) == 'list':
        for dep in deps:
          if hasattr(dep, "output_data"):
           #print("  JSON FILES from attr")
           #print(dep.output_data.json_files)
           json_files += dep.output_data.json_files
           classpath_jars = depset(dep.output_data.classpath_jars.to_list(), transitive = [classpath_jars])
        all_deps += [str(dep.label) for dep in deps]
        hasDepAttr = True

  hasJavaAttr = False
  if JavaInfo in target:
    hasJavaAttr = True
    (java_rule_ide_info_struct, target_classpath_jars) = java_rule_ide_info(target, ctx)
    json_data = struct(
        label = str(target.label),
        kind = rule_kind,
        dependencies = all_deps,
        build_file_artifact_location = ctx.build_file_path,
    ) + java_rule_ide_info_struct
    classpath_jars = depset(target_classpath_jars.to_list(), transitive = [classpath_jars])
    json_file_path = ctx.actions.declare_file(target.label.name + ".bzljavasdk-build.json")
    ctx.actions.write(json_file_path, json_data.to_json())
    #print("  JSON FILE PATH")
    #print(json_file_path)
    json_files += [json_file_path]

  #print(target.label.name+"  Attr State: DEP: %r JAVA: %r" % (hasDepAttr, hasJavaAttr))
  #print("  JSON FILES")
  #print(json_files)
  #print("  CLASSPATH JARS")
  #print(classpath_jars)

  return struct(
      output_groups = {
        "json-files" : depset(json_files),
        "classpath-jars" : classpath_jars,
      },
      output_data = struct(
        json_files = json_files,
        classpath_jars = classpath_jars,
      )
    )

bzljavasdk_aspect = aspect(implementation = _aspect_impl,
    attr_aspects = DEPENDENCY_ATTRIBUTES
)
"""Aspect for Bazel Java SDK.

This aspect produces information for Java programs running on the Bazel Java SDK. This only
produces information for Java targets.

This aspect has two output groups:
  - json-files : produces .bzljavasdk-build.json files that contains information
    about target dependencies and sources files for the IDE.
  - classpath-jars : build the dependencies needed for the build (i.e., artifacts
    generated by Java annotation processors).

An bzljavasdk-build.json file is a json blob with the following keys:
```javascript
{
  // Label of the corresponding target
  "label": "//package:target",
  // Kind of the corresponding target, e.g., java_test, java_binary, ...
  "kind": "java_library",
  // List of dependencies of this target
  "dependencies": ["//package1:dep1", "//package2:dep2"],
  "Path, relative to the workspace root, of the build file containing the target.
  "build_file_artifact_location": "package/BUILD",
  // List of sources file, relative to the execroot
  "sources": ["package/Test.java"],
  // List of jars created when building this target.
  "jars": [jar1, jar2],
  // List of jars generated by java annotation processors when building this target.
  "generated_jars": [genjar1, genjar2]
}
```

Jar files structure has the following keys:
```javascript
{
  // Location, relative to the execroot, of the jar file or null
  "jar": "bazel-out/host/package/libtarget.jar",
  // Location, relative to the execroot, of the interface jar file,
  // containing only the interfaces of the target jar or null.
  "interface_jar": "bazel-out/host/package/libtarget.interface-jar",
  // Location, relative to the execroot, of the source jar file,
  // containing the sources used to generate the target jar or null.
  "source_jar": "bazel-out/host/package/libtarget.interface-jar",
}
```
"""
