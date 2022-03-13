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
package com.salesforce.bazel.eclipse.logging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.helpers.MessageFormatter;

import com.salesforce.bazel.sdk.logging.LoggerFacade;

/**
 * Add Eclipse Platform logging to WARN and ERROR log messages.
 * <p>
 * After years of frustration in asking "where are the logs", this logger now creates a log file at /tmp/bef.log (on
 * Unix platforms) if /tmp exists. It sends the log messages both to the official logger but also to this file which we
 * fully control.
 */
public class EclipseLoggerFacade extends LoggerFacade {
    private static final Bundle BUNDLE = FrameworkUtil.getBundle(EclipseLoggerFacade.class);
    private static final ILog ECLIPSELOGGER = Platform.getLog(BUNDLE);

    // a custom log file in <user_home>/bef.log to duplicate logs in case of loss
    private static BufferedWriter befLogWriter = null;
    private static SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy-HH:mm:ss");

    private static boolean extendedLog = true;

    public static enum LogLevel {
        DEBUG(IStatus.INFO, LoggerFacade.DEBUG, "DEBUG"),
        INFO(IStatus.INFO, LoggerFacade.INFO, "INFO"),
        ERROR(IStatus.ERROR, LoggerFacade.DEBUG, "ERROR"),
        WARN(IStatus.WARNING, LoggerFacade.WARN, "WARN");

        private final int severityCode;
        private final int level;
        private final String name;

        private LogLevel(int severityCode, int level, String messagePrefix) {
            this.severityCode = severityCode;
            this.level = level;
            this.name = messagePrefix;
        }

        public int getSeverityCode() {
            return severityCode;
        }

        public String getName() {
            return name;
        }

        public int getLevel() {
            return level;
        }

        public static LogLevel fromName(String name) {
            for (LogLevel level : LogLevel.values()) {
                if (level.getName().equalsIgnoreCase(name)) {
                    return level;
                }
            }
            return INFO; //default level
        }
    }

    public static void setLevel(String name) {
        LogLevel logLevel = LogLevel.fromName(name);
        setLevel(logLevel.getLevel());
    }

    public static boolean isExtendedLog() {
        return extendedLog;
    }

    public static void setExtendedLog(boolean extendedLog) {
        EclipseLoggerFacade.extendedLog = extendedLog;
    }

    /**
     * Install the facade as the singleton and configure logging system
     *
     * @param bundle
     * @throws Exception
     */
    public static void install(Bundle bundle) throws Exception {
        EclipseLoggerFacade instance = new EclipseLoggerFacade();
        LoggerFacade.setInstance(instance);

        final String home = System.getProperty("user.home");
        if (Objects.nonNull(home)) {
            final File userHome = new File(home);
            if (userHome.exists()) {
                final File logFile = new File(userHome, "bef.log");
                FileWriter writer = new FileWriter(logFile, false);
                befLogWriter = new BufferedWriter(writer);
                writeTmpLog("Starting Bazel-support plugin");
            }
        }
    }

    /**
     * Always use install
     */
    private EclipseLoggerFacade() {}

    @Override
    protected void error(Class<?> from, String message, Object... args) {
        writeLog(from, LogLevel.ERROR, message, args);
    }

    @Override
    protected void error(Class<?> from, String message, Throwable exception, Object... args) {
        writeLog(from, LogLevel.ERROR, message, exception, args);
    }

    @Override
    protected void warn(Class<?> from, String message, Object... args) {
        writeLog(from, LogLevel.WARN, message, args);
    }

    @Override
    protected void info(Class<?> from, String message, Object... args) {
        writeLog(from, LogLevel.INFO, message, args);
    }

    @Override
    protected void debug(Class<?> from, String message, Object... args) {
        writeLog(from, LogLevel.DEBUG, message, args);
    }

    private void writeLog(Class<?> from, LogLevel logLevel, String message, Object... args) {
        writeLog(from, logLevel, message, null, args);
    }

    private void writeLog(Class<?> from, LogLevel logLevel, String message, Throwable exception, Object... args) {
        if(exception == null && args.length > 0)
            exception = args[0] instanceof Throwable ? (Throwable) args[0] : args[args.length-1] instanceof Throwable ? (Throwable)  args[args.length-1] :null;

        String logMessage = "[" + from.getName() + "] " + MessageFormatter.arrayFormat(message, args).getMessage();
        Status logStatus =
                Objects.isNull(exception) ? new Status(logLevel.getSeverityCode(), BUNDLE.getSymbolicName(), logMessage)
                        : new Status(logLevel.getSeverityCode(), BUNDLE.getSymbolicName(), logMessage, exception);
        ECLIPSELOGGER.log(logStatus);
        writeTmpLog(logLevel.getName() + ": " + logMessage);
    }

    private static synchronized void writeTmpLog(String message) {
        try {
            if (befLogWriter != null && EclipseLoggerFacade.isExtendedLog()) {
                String date = formatter.format(new Date());
                befLogWriter.write(date + " " + message + "\n");
                befLogWriter.flush();
            }
        } catch (Exception anyE) {
            // out of disk space, someone deleted /tmp/bef.log, etc
        }
    }

    //TODO
    /**
     * Configure slf4j and logback back using logback.xml at the top level directory
     *
     * @param bundle
     * @throws JoranException
     * @throws IOException
     */
    // WE WILL LEAVE THIS HERE FOR ANOTHER YEAR JUST IN CASE, BUT FEEL FREE TO DELETE AFTER 2022
    //    private void configureLogging(Bundle bundle) throws JoranException, IOException {
    // capture the logger context, but guard against:
    // org.slf4j.helpers.NOPLoggerFactory cannot be cast to ch.qos.logback.classic.LoggerContext
    //        ILoggerFactory iFactory = LoggerFactory.getILoggerFactory();
    //        if (iFactory instanceof LoggerContext) {
    //           LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    //           JoranConfigurator jc = new JoranConfigurator();
    //            jc.setContext(context);
    //            context.reset();
    //
    //            IPath pluginPath = ResourcesPlugin.getPlugin().getStateLocation();
    //            String logFile = new File(pluginPath.toFile(), bundle.getSymbolicName().replace('.', '_') + ".log")
    //                    .getAbsolutePath();
    //            context.putProperty("logFile", logFile);
    //            eclipseOnlyInfo("Debug log file: {}", logFile);
    //
    //            URL logbackConfig = FileLocator.find(bundle, new Path("logback.xml"), null);
    //            if (null == logbackConfig) {
    //                //not found log an event to the error log
    //                error(getClass(), "logback.xml not found. All slf4j output to console");
    //            }
    //            jc.doConfigure(logbackConfig);
    //        } else {
    //            // Logback was missing from the classpath when logging was initialized for some reason
    //
    //            // check classloader to see if it is there now (if it is, there must be a load order issue?)
    //            String isLoggerContextIsInClasspathStr =
    //                    "The class ch.qos.logback.classic.LoggerContext is not present in the classpath which means the plugin never imported it.";
    //            try {
    //                Class.forName("ch.qos.logback.classic.LoggerContext");
    //                isLoggerContextIsInClasspathStr =
    //                        "The class ch.qos.logback.classic.LoggerContext is NOW available in the classpath, which means there was a startup loading issue. The class wasn't there when logging was initialized.";
    //            } catch (Exception cnfe) {}
    //
    //            System.err.println(
    //                "com.salesforce.bazel.eclipse.core: EclipseLoggerFacade could not configure file (INFO, DEBUG) logging. LoggerFactory is of type ["
    //                        + iFactory.getClass()
    //                        + "] but needed [ch.qos.logback.classic.LoggerContext] to configure the file log. "
    //                        + isLoggerContextIsInClasspathStr + " See "
    //                        + "bazel-eclipse/docs/dev/logging.md for more details.");
    //
    //            // set the activator convenience methods to log to sys err
    //            BazelPluginActivator.logToSystemErr();
    //        }
    //    }

    //    private void eclipseOnlyInfo(String message, Object... args) {
    //        String resolved = MessageFormatter.arrayFormat(message, args).getMessage();
    //        LOG.log(new Status(Status.INFO, BUNDLE.getSymbolicName(), resolved));
    //    }

}
