/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.salesforce.bazel.eclipse.core;

import static java.util.Objects.requireNonNull;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import com.salesforce.bazel.eclipse.core.model.BazelModelManager;
import com.salesforce.bazel.eclipse.core.osgi.OsgiServiceTracker;
import com.salesforce.bazel.sdk.init.BazelJavaSDKInit;

/**
 * Plugin activator managing the lifecycle of the Bazel Eclipse headless plug-in.
 * <p>
 * It should not be used outside this plug-in. Instead please use {@link BazelCore}.
 * </p>
 */
public class BazelCorePlugin extends Plugin implements BazelCoreSharedContstants {
    private static volatile BazelCorePlugin plugin;

    private static String bundleVersion;

    public static String getBundleVersion() {
        return requireNonNull(bundleVersion, "Bundle version not initialized. Is the Core bundle properly started?");
    }

    public static BazelCorePlugin getInstance() {
        return requireNonNull(plugin, "plugin not initialized");
    }

    private BazelModelManager bazelModelManager;
    private volatile OsgiServiceTracker serviceTracker;

    public BazelModelManager getBazelModelManager() {
        return requireNonNull(bazelModelManager, "model manager not initialized");
    }

    public OsgiServiceTracker getServiceTracker() {
        return requireNonNull(serviceTracker, "service tracker not initialized");
    }

    public void setServiceTracker(OsgiServiceTracker serviceTracker) {
        this.serviceTracker = serviceTracker;
    }

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        super.start(bundleContext);
        plugin = this;
        bundleVersion = bundleContext.getBundle().getVersion().toString();

        // initialize the SDK
        BazelJavaSDKInit.initialize("Bazel Eclipse Feature");

        // initialize model
        bazelModelManager = new BazelModelManager(getStateLocation());
        bazelModelManager.initialize(ResourcesPlugin.getWorkspace());
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        try {
            bazelModelManager.shutdown();
        } catch (Exception e) {
            // this might fail because other bundles are already stopped
            // at least we tried
        }

        super.stop(context);
    }
}
