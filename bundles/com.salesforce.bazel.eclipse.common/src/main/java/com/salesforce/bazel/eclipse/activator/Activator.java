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
package com.salesforce.bazel.eclipse.activator;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.component.JavaCoreHelperComponentFacade;
import com.salesforce.bazel.eclipse.component.ProjectManagerComponentFacade;
import com.salesforce.bazel.eclipse.component.ResourceHelperComponentFacade;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

/**
 * The activator class controls the Bazel Eclipse plugin life cycle
 */
public class Activator extends Plugin {
    private static Activator plugin;

    // The plug-in ID
    public static final String PLUGIN_ID = "com.salesforce.bazel.eclipse.common"; //$NON-NLS-1$

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        super.start(bundleContext);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }

    public ResourceHelper getResourceHelper() {
        return ResourceHelperComponentFacade.getInstance().getComponent();
    }

    public JavaCoreHelper getJavaCoreHelper() {
        return JavaCoreHelperComponentFacade.getInstance().getComponent();
    }

    public BazelProjectManager getProjectManager() {
        return ProjectManagerComponentFacade.getInstance().getComponent();
    }

    public void log(IStatus status) {
        getLog().log(status);
    }

    public void log(CoreException e) {
        log(e.getStatus());
    }

    public void logError(String message) {
        log(new Status(IStatus.ERROR, getBundle().getSymbolicName(), message));
    }

    public void logInfo(String message) {
        log(new Status(IStatus.INFO, getBundle().getSymbolicName(), message));
    }

    public void logWarning(String message) {
        log(new Status(IStatus.WARNING, getBundle().getSymbolicName(), message));
    }

    public void logException(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) {
            StringWriter stringWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(stringWriter));
            message = stringWriter.toString();
        }
        logException(message, ex);
    }

    public void logException(String message, Throwable ex) {
        log(new Status(IStatus.ERROR, getBundle().getSymbolicName(), message, ex));
    }
}
