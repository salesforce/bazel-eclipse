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

"""An aspect which extracts the runtime classpath from a java target."""

def _runtime_classpath_impl(target, ctx):
    """The top level aspect implementation function.

    Args:
      target: Essentially a struct representing a BUILD target.

      ctx: The context object that can be used to access attributes and generate
      outputs and actions.

    Returns:
      A struct with only the output_groups provider.
    """
    ctx = ctx  # unused argument
    return struct(output_groups = {
        "runtime_classpath": _get_runtime_jars(target),
    })

def _get_runtime_jars(target):
    if JavaInfo not in target:
        return depset()
    if target[JavaInfo].compilation_info:
        return target[JavaInfo].compilation_info.runtime_classpath

    # JavaInfo constructor doesn't fill in compilation info, so just return the
    # full transitive set of runtime jars
    # https://github.com/bazelbuild/bazel/issues/10170
    return target[JavaInfo].transitive_runtime_jars

def _aspect_def(impl):
    return aspect(implementation = impl)

java_classpath_aspect = _aspect_def(_runtime_classpath_impl)
