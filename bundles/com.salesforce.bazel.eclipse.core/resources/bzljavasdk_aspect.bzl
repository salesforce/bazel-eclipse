# Copyright 2021 Salesforce. All rights reserved.
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

"""Bazel-specific intellij aspect."""

load(
    ":intellij_info_impl.bzl",
    "intellij_info_aspect_impl",
    "make_intellij_info_aspect",
)

EXTRA_DEPS = [
    "embed",  # From go rules (bazel only)
]

def tool_label(tool_name):
    """Returns a label that points to a tool target in the bundled aspect workspace."""
    return Label("//:" + tool_name + "_bin")

def get_go_import_path(ctx):
    """Returns the import path for a go target."""
    import_path = getattr(ctx.rule.attr, "importpath", None)
    if import_path:
        return import_path
    prefix = None
    if hasattr(ctx.rule.attr, "_go_prefix"):
        prefix = ctx.rule.attr._go_prefix.go_prefix
    if not prefix:
        return None
    import_path = prefix
    if ctx.label.package:
        import_path += "/" + ctx.label.package
    if ctx.label.name != "go_default_library":
        import_path += "/" + ctx.label.name
    return import_path

def get_py_launcher(ctx):
    """Returns the python launcher for a given rule."""
    attr = ctx.rule.attr
    if hasattr(attr, "_launcher") and attr._launcher != None:
        return str(attr._launcher.label)
    return None

semantics = struct(
    tool_label = tool_label,
    extra_deps = EXTRA_DEPS,
    go = struct(
        get_import_path = get_go_import_path,
    ),
    py = struct(
        get_launcher = get_py_launcher,
    ),
    flag_hack_label = "//:flag_hack",
)

def _aspect_impl(target, ctx):
    return intellij_info_aspect_impl(target, ctx, semantics)

bzleclipse_aspect = make_intellij_info_aspect(_aspect_impl, semantics)
