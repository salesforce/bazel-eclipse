# Copyright 2017 The Bazel Authors. All rights reserved.
# Copyright 2018 Salesforce. All rights reserved.
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
# This download facility is only used by the updatesite rule.
# We are not using Bazel to build the feature updatesite any more, and likely never will.
# See the //docs/dev/threebuilds.md file for an explanation about it.
#



# Eclipse SDK Version
# The Eclipse website provides SHA-512 but Bazel only support SHA256.
# Really Bazel should start supporting all "safe" checksum (and also
# drop support for SHA-1).
SHA256_SUM={
    # The original plugin was written against 4.5.2, but we (Salesforce)
    # have now upgraded to Eclipse SDK 4.9 as of Dec 2018
     "4.5.2": {
        "macosx-cocoa-x86_64": "755f8a75075f6310a8d0453b5766a84aca2fcc687808341b7a657259230b490f",
        "linux-gtk-x86_64": "87f82b0c13c245ee20928557dbc4435657d1e029f72d9135683c8d585c69ba8d"
    },
    "4.9": {
        "macosx-cocoa-x86_64": "181b6a8948126a1a10999e7df23e2746aef3d2a6b84a3c1effe24411ef0bd03e",
        "linux-gtk-x86_64": "dd9f84b2fd8bb6b9e2cb59f0766286709c99ca0bf50d49f2570506fd3da1ab9a"
    }
}

def _get_file_url(version, platform, t):
  drop = "drops"
  if int(version.split(".", 1)[0]) >= 4:
    drop = "drops4"
  short_version = version.split("-", 1)[0]
  sha256 = ""
  if short_version in SHA256_SUM:
    if platform in SHA256_SUM[short_version]:
      sha256 = SHA256_SUM[short_version][platform]

  filename = "eclipse-SDK-%s-%s.%s" % (short_version, platform, t)
  file = "/eclipse/%s/%s" % (
      version,
      filename)
  # This is a Salesforce internal mirror (private cloud node bazel-cache-secure)
  # Original base url is http://www.eclipse.org/downloads/download.php?file=
  base_url = "https://bazelcache01-xrd.eng.sfdc.net"

  return (base_url + file, sha256)


def _eclipse_platform_impl(rctx):
  # this rule is actually invoked by eclipse.bzl, but ultimately from the WORKSPACE load_eclipse_deps()
  print("DOWNLOAD ECLIPSE PLATFORM (invoked by WORKSPACE)")

  # the repository context (rctx) is documented here:
  # https://docs.bazel.build/versions/master/skylark/lib/repository_ctx.html

  # COMPUTE VERSION OF ECLIPSE TO USE
  version = rctx.attr.version
  os_name = rctx.os.name.lower()
  if os_name.startswith("mac os"):
    platform = "macosx-cocoa-x86_64"
    t = "tar.gz"
  elif os_name.startswith("linux"):
    platform = "linux-gtk-x86_64"
    t = "tar.gz"
  else:
    fail("Cannot fetch Eclipse for platform %s" % rctx.os.name)
  url, sha256 = _get_file_url(version, platform, t)

  # DOWNLOAD ECLIPSE INSTALLER
  print("  _eclipse_platform_impl url for Eclipse download: "+url)
  rctx.download_and_extract(url=url, type=t, sha256=sha256)

  # INJECT EXTRACTED ECLIPSE INSTALL INTO BAZEL BUILD GRAPH
  # This next section is some real magic. It generates a BUILD file on the fly.
  # (aside: BUILD.bazel is a legal alternative name for a BUILD file)
  # The BUILD file declares the downloaded Eclipse platform and launcher jars into Bazel.
  # Generating a BUILD file works because this rule is a repository rule, and therefore
  # is run before the build graph is built. It writes the BUILD.bazel file into the
  # following location:
  #   [execution_root]/org_eclipse_equinox/BUILD.bazel
  # Use 'bazel info' to find your execution root
  # https://docs.bazel.build/versions/master/output_directories.html
  rctx.file("BUILD.bazel", """
package(default_visibility = ["//visibility:public"])
filegroup(name = "platform", srcs = glob(["**"], exclude = ["BUILD.bazel", "BUILD"]))
filegroup(name = "launcher", srcs = glob(["**/plugins/org.eclipse.equinox.launcher_*.jar"]))
""")


eclipse_platform = repository_rule(
  implementation = _eclipse_platform_impl,
  attrs = {
    "version": attr.string(mandatory=True),
  }, local=False)
"""A repository for downloading the good version eclipse depending on the platform."""
