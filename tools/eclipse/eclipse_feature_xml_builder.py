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
"""This tool build feature.xml files describing an Eclipse feature."""

_VERBOSE=False

import sys
from xml.etree import ElementTree
from xml.dom import minidom
import tools.gflags.gflags as gflags

gflags.DEFINE_string("output", None, "The output file, mandatory")
gflags.MarkFlagAsRequired("output")

gflags.DEFINE_string("id", None, "The feature ID, mandatory")
gflags.MarkFlagAsRequired("id")

gflags.DEFINE_string(
  "label",
  None,
  "The feature label (i.e. short description), mandatory")
gflags.MarkFlagAsRequired("label")

gflags.DEFINE_string("version", None, "The feature version, mandatory")
gflags.MarkFlagAsRequired("version")

gflags.DEFINE_string(
  "provider", None, "The provider (i.e. the vendor) of the feature, mandatory")
gflags.MarkFlagAsRequired("provider")

gflags.DEFINE_string(
  "url", None, "A URL associated to the description, optional")

gflags.DEFINE_string(
  "description", None, "Description of the feature, mandatory")
gflags.MarkFlagAsRequired("description")

gflags.DEFINE_string(
  "copyright", None, "Copyright line for the repository, mandatory")
gflags.MarkFlagAsRequired("copyright")

gflags.DEFINE_string(
  "license_url", None, "URL pointing to the license, mandatory")
gflags.MarkFlagAsRequired("license_url")

gflags.DEFINE_string(
  "license", None, "Text file of the license of the feature, mandatory")
gflags.MarkFlagAsRequired("license")

gflags.DEFINE_multistring(
  "site", [], "Sites related to the plugin, in the form `label=url`")
gflags.DEFINE_multistring(
  "plugin", [], "List of plugins that this feature contains (filename).")

FLAGS=gflags.FLAGS

def verbose(message):
   if _VERBOSE: print(message)

def _plugins(parent, plugins):
  for plugin in plugins:
    if plugin.endswith(".jar"):
      id, version = plugin[:-4].split("_", 1)
      p = ElementTree.SubElement(parent, "plugin")
      p.set("id", id)
      p.set("download-size", "0")
      p.set("install-size", "0")
      p.set("version", version)
      # eventually each plugin should provide the unpack setting
      # see https://git.soma.salesforce.com/services/bazel-eclipse/pull/65/files for why
      # we set this to true here
      p.set("unpack", "true")


def _sites(parent, sites):
  for site in sites:
    label, url = site.split("=", 1)
    p = ElementTree.SubElement(parent, "discovery")
    p.set("label", label)
    p.set("url", url)


def main(unused_argv):
  verbose("DEBUG:  feature_builder.py GENERATE FEATURE")
  feature = ElementTree.Element("feature")
  feature.set("id", FLAGS.id)
  feature.set("label", FLAGS.label)
  feature.set("version", FLAGS.version)
  feature.set("provider-name", FLAGS.provider)
  description = ElementTree.SubElement(feature, "description")
  if FLAGS.url:
    description.set("url", FLAGS.url)
  description.text = FLAGS.description
  copyright = ElementTree.SubElement(feature, "copyright")
  copyright.text = FLAGS.copyright
  license = ElementTree.SubElement(feature, "license")
  license.set("url", FLAGS.license_url)
  with open(FLAGS.license, "r") as f:
    license.text = f.read()
  _sites(ElementTree.SubElement(feature, "url"), FLAGS.site)
  _plugins(feature, FLAGS.plugin)

  # Pretty print the resulting tree
  output = ElementTree.tostring(feature, "utf-8")
  reparsed = minidom.parseString(output)
  with open(FLAGS.output, "wb") as f:
    f.write(reparsed.toprettyxml(indent="  ", encoding="UTF-8"))

if __name__ == "__main__":
  main(FLAGS(sys.argv))
