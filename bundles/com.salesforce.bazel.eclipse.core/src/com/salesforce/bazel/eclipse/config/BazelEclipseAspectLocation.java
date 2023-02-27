/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.eclipse.config;

import java.io.File;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Implementation of {@link BazelAspectLocation} using Eclipse OSGi Bundle locations.
 * <p>
 * The Bazel Eclipse plugin uses a Bazel Aspect to introspect build data, such as dependencies. See
 * {@link BazelWorkspaceCommandRunner} for details on how this aspect is wired into the build.
 */
public class BazelEclipseAspectLocation implements BazelAspectLocation {

    private static Logger LOG = LoggerFactory.getLogger(BazelEclipseAspectLocation.class);

    public static final String SDK_PLUGIN_ID = "com.salesforce.bazel-java-sdk"; //$NON-NLS-1$

    private File WORKSPACE_DIRECTORY;

    @Override
    public synchronized File getAspectDirectory() {
        if (WORKSPACE_DIRECTORY == null) {
            WORKSPACE_DIRECTORY = getAspectWorkspace();
        }
        return WORKSPACE_DIRECTORY;
    }

    @Override
    public String getAspectLabel() {
        return "//:bzljavasdk_aspect.bzl%bzljavasdk_aspect";
    }

    /**
     * Returns the path of the Aspect file(s) from the SDK plugin, which needs to be extracted on the filesystem.
     * <p>
     * This is easily broken by changes in Eclipse packaging, so be careful and validate the location. This
     * implementation assumes this plugin was been installed 'unpacked' in the p2 cache. This takes some effort. See
     * https://stackoverflow.com/questions/922230/how-do-i-force-an-eclipse-plug-in-to-be-unpacked ($SLASH_OK url) for
     * some hints if this breaks.
     * <p>
     * Throws an exception if the Aspect cannot be located on the filesystem, which will prevent the plugin from
     * loading. Make sure to log the reason(s) before throwing otherwise the user won't be able to troubleshoot the
     * problem. This type of problem should never make it out of the lab because it is a serious packaging error if this
     * happens.
     */
    private File getAspectWorkspace() {
        try {
            var bazelSDKPlugin = Platform.getBundle(SDK_PLUGIN_ID);
            if (bazelSDKPlugin == null) {
                throw new IllegalStateException(
                        "Eclipse OSGi subsystem could not find the Bazel SDK plugin [" + SDK_PLUGIN_ID + "]");
            }
            var url = bazelSDKPlugin.getEntry("aspect");
            var resolved = getResolvedPath(url);
            if (resolved == null) {
                throw new IllegalStateException("Could not load BEF Aspect location [" + url + "]");
            }

            var aspectPath = resolved.getPath();
            var aspectWorkspaceDirFile = new File(aspectPath);

            // verify the aspect is available extractd on the file system
            if (!aspectWorkspaceDirFile.exists()) {
                var canonicalFile = FSPathHelper.getCanonicalFileSafely(new File(aspectPath));
                if (!canonicalFile.exists()) {
                    if (!aspectPath.contains("com.salesforce.bazel-java-sdk" + File.separator + "target")) {
                        // This is a critical piece of validation. If the packaging breaks and the Aspect is no longer
                        // on the filesystem, we need to fail. The Aspect is critical to the computation of the classpath,
                        // so nothing will work without it. Fail hard if it is missing.
                        throw new IllegalStateException("Could not load the BEF Aspect on disk ["
                                + aspectWorkspaceDirFile.getAbsolutePath() + "]");
                    }
                    // this is a test bundle for unit tests, it is ok that we cant load the aspect because it is not
                    // properly packaged as a feature
                    LOG.debug(
                        "Detected running as test bundle, the aspect files are not available but this is expected: ["
                                + aspectPath + "]");
                }
            }
            LOG.debug("BEF Aspect location: [" + aspectWorkspaceDirFile.getAbsolutePath() + "]");

            return aspectWorkspaceDirFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method resolves the given url into a required protocol-specififc URL (i.e. file protocol with files
     * extraction like {@link FileLocator#toFileURL} or protocol that is nativve to the Java class library like
     * {@link FileLocator#resolve}
     *
     * @param url
     *            - initial {@link URL}
     * @return - resolved {@link URL}
     */
    protected URL getResolvedPath(URL url) throws Exception {
        return FileLocator.resolve(url);
    }
}
