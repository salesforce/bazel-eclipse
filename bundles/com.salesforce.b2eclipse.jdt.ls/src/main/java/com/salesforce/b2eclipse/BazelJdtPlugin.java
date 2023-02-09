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
package com.salesforce.b2eclipse;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.handlers.MapFlattener;
import org.eclipse.jdt.ls.core.internal.preferences.IPreferencesChangeListener;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.Preferences;

import com.salesforce.b2eclipse.config.IPreferenceConfiguration;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseComponentContextInitializer;
import com.salesforce.bazel.eclipse.logging.EclipseLoggerFacade;
import com.salesforce.bazel.eclipse.logging.EclipseLoggerFacade.LogLevel;
import com.salesforce.bazel.sdk.console.StandardCommandConsoleFactory;
import com.salesforce.bazel.sdk.init.BazelJavaSDKInit;
import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * The activator class controls the Bazel Eclipse plugin life cycle
 */
@SuppressWarnings("restriction")
public class BazelJdtPlugin extends Plugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "com.salesforce.b2eclipse.jdt.ls"; //$NON-NLS-1$
    private static BazelJdtPlugin plugin;

    // GLOBAL COLLABORATORS
    // TODO move the collaborators to some other place, perhaps a dedicated static context object

    public static final String PREFERENCE_WORKSPACE_ROOT_DIRECTORY = "bazelWorkspaceRootDirectory";

    private static Preferences pluginPreferences =
            Platform.getPreferencesService().getRootNode().node(InstanceScope.SCOPE).node(PLUGIN_ID);

    // Command to find bazel path on windows
    public static final String WIN_BAZEL_FINDE_COMMAND = "where bazel";

    // Command to find bazel path on linux or mac
    public static final String LINUX_BAZEL_FINDE_COMMAND = "which bazel";

    public static final String BAZEL_EXECUTABLE_ENV_VAR = "BAZEL_EXECUTABLE";

    public static final String BAZEL_EXECUTABLE_DEFAULT_PATH = "/usr/local/bin/bazel";

    private IPreferencesChangeListener preferencesChangeListener;

    // LIFECYCLE

    /**
     * The constructor
     */
    public BazelJdtPlugin() {

    }

    public static BazelJdtPlugin getDefault() {
        return plugin;
    }

    /*
     * (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        super.start(bundleContext);

        plugin = this;

        preferencesChangeListener = (newPrefs, oldPrefs) -> configureLogging();
        JavaLanguageServerPlugin.getPreferencesManager().addPreferencesChangeListener(preferencesChangeListener);

        initLoggerFacade();

        BazelJavaSDKInit.initialize("Bazel Language Server", "bzl_ls");
        JvmRuleInit.initialize();

        new EclipseComponentContextInitializer(getBundle().getSymbolicName(), new StandardCommandConsoleFactory(), getStateLocation())
                .initialize();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (Objects.nonNull(preferencesChangeListener)
                && Objects.nonNull(JavaLanguageServerPlugin.getPreferencesManager())) {
            JavaLanguageServerPlugin.getPreferencesManager().removePreferencesChangeListener(preferencesChangeListener);
        }
        super.stop(context);
    }

    public static String getEnvBazelPath() {
        return System.getenv(BAZEL_EXECUTABLE_ENV_VAR);
    }

    /**
     * Provides details of the operating environment (OS, real vs. tests, etc)
     */
    public static OperatingEnvironmentDetectionStrategy getOperatingEnvironmentDetectionStrategy() {
        return ComponentContext.getInstance().getOsStrategy();
    }

    // COLLABORATORS
    // TODO move the collaborators to some other place, perhaps a dedicated static context object

    public static File getBazelWorkspaceRootDirectoryOnStart() {
        String path = pluginPreferences.get(PREFERENCE_WORKSPACE_ROOT_DIRECTORY, null);
        return path != null ? new File(path) : null;
    }

    public Map<String, Object> getJdtLsPreferences() {
        Map<String, Object> prefs = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().asMap();
        return Objects.nonNull(prefs) ? prefs : Collections.emptyMap();
    }

    private void initLoggerFacade() throws Exception {
        EclipseLoggerFacade.install(getBundle());
        configureLogging();
    }

    private void configureLogging() {
        final Map<String, Object> jdtlsConfig = getJdtLsPreferences();
        String level =
                MapFlattener.getString(jdtlsConfig, IPreferenceConfiguration.BJLS_LOG_LEVEL, LogLevel.INFO.getName());
        boolean extended = MapFlattener.getBoolean(jdtlsConfig, IPreferenceConfiguration.BJLS_LOG_EXTENDED, true);
        EclipseLoggerFacade.setLevel(level);
        EclipseLoggerFacade.setExtendedLog(extended);
    }
}
