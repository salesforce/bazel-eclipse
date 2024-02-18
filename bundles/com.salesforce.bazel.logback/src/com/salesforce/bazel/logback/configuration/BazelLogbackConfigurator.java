/*-
 * Copyright (c) 2010, 2022 Sonatype, Inc and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *      Hannes Wellmann - Merge m2e.logback.configuration into .appender
 *      Salesforce - adapted for Bazel Eclipse Feature (https://github.com/eclipse-m2e/m2e-core/pull/1366)
 */
package com.salesforce.bazel.logback.configuration;

import java.net.URL;
import java.nio.file.Files;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.environment.EnvironmentInfo;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ConfiguratorRank;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

@ConfiguratorRank(value = ConfiguratorRank.CUSTOM_HIGH_PRIORITY)
public class BazelLogbackConfigurator extends BasicConfigurator implements Configurator {

    private static final ILog LOG = Platform.getLog(BazelLogbackConfigurator.class);

    private static final String RESOURCES_PLUGIN_ID = "org.eclipse.core.resources"; //$NON-NLS-1$

    // This has to match the log directory in defaultLogbackConfiguration/logback.xml
    private static final String PROPERTY_LOG_DIRECTORY = "com.salesforce.bazel.log.dir"; //$NON-NLS-1$

    // This has to match the log directory in defaultLogbackConfiguration/logback.xml
    private static final String PROPERTY_LOG_CONSOLE_THRESHOLD = "com.salesforce.bazel.log.console.threshold"; //$NON-NLS-1$

    private static void applyDebugLogLevels(LoggerContext lc) {
        ServiceTracker<DebugOptions, Object> tracker = openServiceTracker(DebugOptions.class);
        try {
            var debugOptions = (DebugOptions) tracker.getService();
            if (debugOptions != null) {
                var options = debugOptions.getOptions();
                for (Entry<String, String> entry : options.entrySet()) {
                    var key = entry.getKey();
                    var value = entry.getValue();
                    if (key.endsWith("/debugLog") && "true".equals(value)) {
                        lc.getLogger(key.replace("/debugLog", "")).setLevel(Level.DEBUG);
                    }
                }
            }
        } finally {
            tracker.close();
        }
    }

    private static void disableBundle(Bundle bundle) {
        if ((bundle == null) || (bundle.getState() == Bundle.UNINSTALLED)) {
            return;
        }

        try {
            bundle.uninstall();
        } catch (BundleException e) {
            LOG.warn("Unable to stop bundle: " + bundle.getSymbolicName(), e);
        }
    }

    private static boolean isStateLocationInitialized() {
        if (!Platform.isRunning()) {
            return false;
        }
        var resourcesBundle = Platform.getBundle(RESOURCES_PLUGIN_ID);
        return (resourcesBundle != null) && (resourcesBundle.getState() == Bundle.ACTIVE);
    }

    private static void loadConfiguration(LoggerContext lc, URL configFile) throws JoranException {
        lc.reset();

        var configurator = new JoranConfigurator();
        configurator.setContext(lc);
        configurator.doConfigure(configFile);

        StatusPrinter.printInCaseOfErrorsOrWarnings(lc);

        applyDebugLogLevels(lc);

        logJavaProperties(LoggerFactory.getLogger(BazelLogbackConfigurator.class));
    }

    private static void logJavaProperties(Logger log) {
        var javaProperties = System.getProperties();
        SortedMap<String, String> sortedProperties = new TreeMap<>();
        for (String key : javaProperties.stringPropertyNames()) {
            sortedProperties.put(key, javaProperties.getProperty(key));
        }
        log.debug("Java properties (ordered by property name):"); //$NON-NLS-1$
        sortedProperties.forEach((k, v) -> log.debug("   {}={}", k, v));
    }

    private static <T> ServiceTracker<T, Object> openServiceTracker(Class<T> serviceClass) {
        var bundle = Platform.getBundle("com.salesforce.bazel.eclipse.core"); // fragments don't have a BundleContext
        var tracker = new ServiceTracker<>(bundle.getBundleContext(), serviceClass, null);
        tracker.open();
        return tracker;
    }

    private static void runConditionally(Runnable action, BooleanSupplier condition, String name) {
        var timer = new Timer(name);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (condition.getAsBoolean()) {
                    timer.cancel();
                    action.run();
                }
            }
        }, 0 /*delay*/, 50 /*period*/);
    }

    @Override
    public ExecutionStatus configure(LoggerContext lc) {
        // Bug 337167: Configuring Logback requires the state-location. If not yet initialized it will be initialized to the default value,
        // but this prevents the workspace-chooser dialog to show up in a stand-alone Eclipse-product. Therefore we have to wait until the resources plug-in has started.
        // This happens if a Plug-in that uses SLF4J is started before the workspace has been selected.
        if (!isStateLocationInitialized()) {
            super.configure(lc); // Preliminary apply default configuration
            LOG.info(
                "Activated before the state location was initialized. Retry after the state location is initialized."); //$NON-NLS-1$

            runConditionally(
                () -> configureLogback(lc),
                BazelLogbackConfigurator::isStateLocationInitialized,
                "logback configurator timer");
        } else {
            configureLogback(lc);
        }

        return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
    }

    private synchronized void configureLogback(LoggerContext lc) {
        try {
            // https://github.com/eclipse-m2e/m2e-core/pull/1366
            // check for m2e logback bundle and override its configuration
            // we tried to integrate but M2E developers not very receptive
            disableBundle(Platform.getBundle("org.eclipse.m2e.logback"));

            // Eclipse 2023-09 started to ship SLF4J Simple logger implementation
            // disable it as well because we really want log event to go to the Eclipse .log
            disableBundle(Platform.getBundle("slf4j.simple"));

            // JDT LS has its own Logback configuration which prevents others from integrating with it
            disableBundle(Platform.getBundle("org.eclipse.jdt.ls.logback.appender"));

            var bundle = Platform.getBundle("com.salesforce.bazel.logback"); // This is a fragment -> FrameworkUtil.getBundle() returns host
            var stateDir = Platform.getStateLocation(bundle).toPath();
            var configFile = stateDir.resolve("logback." + bundle.getVersion() + ".xml"); //$NON-NLS-1$  //$NON-NLS-2$
            LOG.info("Logback config file: " + configFile.toAbsolutePath()); //$NON-NLS-1$

            if (!Files.isRegularFile(configFile)) {
                // Copy the default config file to the actual config file, to allow user adjustments
                try (var is = bundle.getEntry("defaultLogbackConfiguration/logback.xml").openStream()) { //$NON-NLS-1$
                    Files.createDirectories(configFile.getParent());
                    Files.copy(is, configFile);
                }
            }
            if (System.getProperty(PROPERTY_LOG_DIRECTORY, "").length() <= 0) { //$NON-NLS-1$
                System.setProperty(PROPERTY_LOG_DIRECTORY, stateDir.toAbsolutePath().toString());
            }
            if ((System.getProperty(PROPERTY_LOG_CONSOLE_THRESHOLD, "").length() <= 0) && isConsoleLogEnable()) {
                System.setProperty(PROPERTY_LOG_CONSOLE_THRESHOLD, Level.DEBUG.levelStr);
            }
            loadConfiguration(lc, configFile.toUri().toURL());
        } catch (Exception e) {
            LOG.log(Status.warning("Exception while setting up logging:" + e.getMessage(), e));
        }
    }

    private boolean isConsoleLogEnable() {
        ServiceTracker<EnvironmentInfo, Object> tracker = openServiceTracker(EnvironmentInfo.class);
        try {
            var environmentInfo = (EnvironmentInfo) tracker.getService();
            return (environmentInfo != null) && "true".equals(environmentInfo.getProperty("eclipse.consoleLog")); //$NON-NLS-1$
        } finally {
            tracker.close();
        }
    }

}
