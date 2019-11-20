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

# Changing versions of Eclipse? This is the place to start.
# If you change anything below (add/remove/update) make sure the change is reflected in
# plugin-core/META-INF/MANIFEST.MF

ECLIPSE_VERSION="4.9"

# To upgrade:
# 1. locate and identify the correct version of each of these dependencies from the target SDK
# 2. update the version string below (the SHA is no longer used, it can be ignored)
# 3. copy the jar to the jars subdirectory
# 4. regenerate the BUILD file (see the _load_eclipse_dep function below)

_ECLIPSE_PLUGIN_DEPS = {

  # Eclipse platform jar
  # we only use SWT classes out of here so it is not an issue that this one is a Mac specific jar
  "org.eclipse.swt.cocoa.macosx.x86_64": { "version": "3.108.0.v20180904-1901", "sha256": "c29bc1d33c73e32dd72e8aa8eacdd3c801c84e56e973503a8b5ae49d518f41c2" },

  # Eclipse Features/Plugins/Libs

  "javax.annotation": { "version": "1.2.0.v201602091430", "sha256": "08a9e61914ebd778975bbf16f199fb9fe26c8e637506d54ae8d2d61d930f8656" },
  "javax.inject": { "version": "1.0.0.v20091030", "sha256": "0016231e7908290153ec624da0dfe62942c91e18af0a57bd71555b7f9e6093d6" },

  "org.eclipse.core.commands": { "version": "3.9.200.v20180827-1727", "sha256": "66e3c28060f69bcd97eee01cbbebf1f2b4b5cbcd5c3ee145c7800941ed5b01fa" },
  "org.eclipse.core.contenttype": { "version": "3.7.100.v20180817-1401", "sha256": "e22882180832c520b828bb1bd6d2a9eaa263ac4b000ee9635cc25090c4598d85" },
  "org.eclipse.core.expressions": {"version": "3.6.200.v20180817-1401", "sha256":"d27e29d6b765f060fc8f77a5a5152d26dfe2980f853f2055dd44ff64e7f2e202" },
  "org.eclipse.core.jobs": { "version": "3.10.100.v20180817-1215", "sha256": "929ee2072dd716bc901ef2b5fad7f0e9c860e21b8c406a7e83b08bfe1fe6d30d" },
  "org.eclipse.core.resources": { "version": "3.13.100.v20180828-0158", "sha256": "40957aca247081489bb519fff63164035ae46d6e1864273714f2977f7713d243" },
  "org.eclipse.core.runtime": { "version": "3.15.0.v20180817-1401", "sha256": "50f839bda2b3e83f66c062037b4df6d5727a94c68f622715a6c10d36b2cf743a" },

  "org.eclipse.debug.core": { "version": "3.13.0.v20180821-1744", "sha256": "b289c5887be95d8ed90d6517bf470984df7b611484984084660b909e3eff8b24" },
  "org.eclipse.debug.ui": { "version": "3.13.100.v20180827-0649", "sha256": "7a8a1b91102364d179b1d26b894ab447977505ae23301b882fb8dfc22cb9ec77" },

  "org.eclipse.e4.ui.workbench3": { "version": "0.14.200.v20180828-0227", "sha256": "5d6b912279917ea6ab0a4f00d022c05b39c09ece059591787d860818aef083f9" },
  "org.eclipse.equinox.app": { "version": "1.3.600.v20180827-1235", "sha256": "56663d6203de0996691004536bc62165c7033323af1f44ea06dec67313d03152" },
  "org.eclipse.equinox.common": { "version": "3.10.100.v20180827-1235", "sha256": "87ef393c6693a5104ec6132816b25098685703f28b71a5ba36d67fa23a44be74" },
  "org.eclipse.equinox.registry": { "version": "3.8.100.v20180827-1235", "sha256": "938cd42fa4a12a5544e74b455b016b8b925205b499af9f873238622d78473033" },
  "org.eclipse.equinox.preferences": { "version": "3.7.200.v20180827-1235", "sha256": "93c227ed2b6780d605ff48e93add77db00083f9b98a19392c6123b08caadbabd" },
  "org.eclipse.jface": { "version": "3.14.100.v20180828-0836", "sha256": "372e38db82d248af007563578ceab3a7c0251057b0f2e2abc5fe337bf846d808" },

  "org.eclipse.jdt.compiler.apt": { "version": "1.3.300.v20180905-0315", "sha256": "9a5290e6effec1d53bcf7ff49b11165cb43fa297161b2a303e25921e75784e1e" },
  "org.eclipse.jdt.compiler.tool": { "version": "1.2.300.v20180905-0319", "sha256": "59ae66ab605c460495575ba7ef7d558ee761e482382e19b840079c3658a06383" },
  "org.eclipse.jdt.core": { "version": "3.15.0.v20180905-0317", "sha256": "f600d0289fcf38d5dc02d7e7a311bf24cd7975b33c377f6f2d3caa5b369ada20" },
  "org.eclipse.jdt.junit": { "version": "3.11.200.v20181016-1025", "sha256": "4ddd6baee92f1ab4d3ed53842e96326e5ae478e8cd547525fc68b5fd4293477b" },
  "org.eclipse.jdt.launching": { "version": "3.11.0.v20180827-1040", "sha256": "eae553ea69157606ae0630a3b74d07bff8460432855be95550823db727c7ba30" },

  "org.eclipse.osgi": { "version": "3.13.100.v20180827-1536", "sha256": "8ce5907ed7fdc1828263b616c7700cff933894d26974e8b7ee4a84d7c32e8dd3" },
  "org.eclipse.osgi.compatibility.state": { "version": "1.1.200.v20180827-1536", "sha256": "82c7c897203277bb8c1914654553909f4018c6b93fc861edeb5a5042a1b468a4" },

  "org.eclipse.swt": { "version": "3.108.0.v20180904-1901", "sha256": "d8e5fab8d6933c595718e06c3545e4a3d9be201df93e96cddb8d753076637c20" },
  "org.eclipse.ui": { "version": "3.110.0.v20180828-1350", "sha256": "010a20415a99c9764e44a3dbfe323afd55af867dd9e2e5995882650f16955b90" },
  "org.eclipse.ui.console": { "version": "3.8.100.v20180821-1744", "sha256": "d1a11a939dd370d6f54a49501a97a73753dd415c9f645a72c323e03edced5c9b"},
  "org.eclipse.ui.ide": { "version": "3.14.100.v20180828-1350", "sha256": "339800ef411b0b29dde342fd71216932d8c0a88d40d0c1237a7ba6912698224b" },
  "org.eclipse.ui.views": { "version": "3.9.200.v20180828-0837", "sha256": "7e288eda038961940f432bfa6c5b53571e97824b2f755a08e7e893688b45f075" },
  "org.eclipse.ui.workbench": { "version": "3.112.0.v20180906-1121", "sha256": "4283006bac8be991cbe336666c2fabb8a557e6be4253390b79ca671f87155f7c" },

  # NEED TO ADD ANOTHER ECLIPSE JAR?
  # Find the version of that dep from the Eclipse SDK plugins dir (version is in the filename)
  # (e.g. /Users/mbenioff/dev/tools/sdk49/Eclipse.app/Contents/Eclipse/plugins or $HOME/.p2/pool/plugins)
  # and add the entry above. But wait, there is more...
  # You will also need to add any new entries to the BUILD file
}

def get_eclipse_jar_list():
  return _ECLIPSE_PLUGIN_DEPS

# List of imports to write into the generated MANIFEST.MF for the core plugin
# TODO this needs to be put back in sync with plugin-core/META-INF/MANIFEST.MF
_MANIFEST_DECLARED_DEPS = [
    "javax.inject",
    "org.eclipse.core.resources",
    "org.eclipse.core.runtime",
    "org.eclipse.jdt.core",
    "org.eclipse.ui",
    "org.eclipse.ui.console",
    "org.eclipse.ui.ide",
]

def get_eclipse_declared_deps_for_manifest():
  return _MANIFEST_DECLARED_DEPS


# Loading Dependencies
# Formerly, this was done via HTTP urls, but no longer.
# These methods remain but don't do anything anymore other than output an updated BUILD file if desired

def _load_eclipse_dep(plugin, version, sha256):
  jar_name = plugin.replace(".", "_")
  jar_filename = "jars/"+plugin+"_"+version+".jar"

  # use this log line to rebuild the BUILD file if you update the Jar list above
  # just copy the new output into the BUILD file
  #print("java_import( name = '"+jar_name+"', jars = ['"+jar_filename+"'], visibility = ['//visibility:public'],)")


# Load the Eclipse Dependencies necessary to build the feature/plugins
# This function is invoked by the WORKSPACE file (so, every command invocation)
def load_eclipse_deps():
  for plugin in _ECLIPSE_PLUGIN_DEPS:
    _load_eclipse_dep(plugin, _ECLIPSE_PLUGIN_DEPS[plugin].get("version"), _ECLIPSE_PLUGIN_DEPS[plugin].get("sha256"))
