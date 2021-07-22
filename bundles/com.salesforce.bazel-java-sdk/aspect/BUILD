# Copyright 2017 The Bazel Authors. All rights reserved.
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
# Description:
# The final form of the BUILD file accessed at runtime as an external WORKSPACE.
#

licenses(["notice"])  # Apache 2.0

load(":intellij_info_impl.bzl",
     "define_flag_hack")

java_binary(
    name = "JarFilter_bin",
    main_class = "com.google.idea.blaze.aspect.JarFilter",
    runtime_deps = [":jar_filter_lib"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "jar_filter_lib",
    jars = ["tools/JarFilter_deploy.jar"],
)

java_binary(
    name = "PackageParser_bin",
    main_class = "com.google.idea.blaze.aspect.PackageParser",
    runtime_deps = [":package_parser_lib"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "package_parser_lib",
    jars = ["tools/PackageParser_deploy.jar"],
)

java_binary(
    name = "CreateAar_bin",
    main_class = "com.google.idea.blaze.aspect.CreateAar",
    runtime_deps = [":jar_filter_lib"],
    visibility = ["//visibility:public"],
)


java_import(
    name = "create_aar_lib",
    jars = ["tools/CreateAar_deploy.jar"],
)

define_flag_hack()
