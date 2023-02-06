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
package com.salesforce.bazel.sdk.logging;

/**
 * Logger facade.
 * <p>
 * The SDK uses this generic logger interface so the calling environment can use whatever logging it chooses. The
 * default logger is the BasicLoggerFacade.
 * <p>
 * You can set the level: 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR
 *
 * @deprecated Replace with SLF4J
 */
public abstract class LoggerFacade {

    public static final int DEBUG = 0;
    public static final int INFO = 1;
    public static final int WARN = 2;
    public static final int ERROR = 3;

    /**
     * Configure the default logger, which just logs to stdout and stderr.
     */
    private static LoggerFacade instance = new BasicLoggerFacade();

    /**
     * Call once at startup to replace the default facade with one more appropriate to the environment.
     */
    public static void setInstance(LoggerFacade newFacade) {
        instance = newFacade;
    }

    /**
     * Logging level, default is INFO.
     */
    private static int level = INFO;

    /**
     * Default instance, this can change - DO NOT CACHE or STORE
     *
     * @return
     */
    public static LoggerFacade instance() {
        return instance;
    }

    /**
     * Log using the provided level. Args are inserted into the message using the {} pattern.
     */
    public void log(int level, Class<?> from, String message, Object... args) {
        if (getLevel() <= level) {
            switch (level) {
            case DEBUG:
                debug(from, message, args);
                break;
            case INFO:
                info(from, message, args);
                break;
            case WARN:
                warn(from, message, args);
                break;
            default:
                error(from, message, args);
            }
        }
    }

    /**
     * Log an error message. Args are inserted into the message using the {} pattern.
     */
    protected abstract void error(Class<?> from, String message, Object... args);

    /**
     * Log an error message. Args are inserted into the message using the {} pattern.
     */
    protected abstract void error(Class<?> from, String message, Throwable exception, Object... args);

    /**
     * Log a warning message. Args are inserted into the message using the {} pattern.
     */
    protected abstract void warn(Class<?> from, String message, Object... args);

    /**
     * Log an info message. Args are inserted into the message using the {} pattern.
     */
    protected abstract void info(Class<?> from, String message, Object... args);

    /**
     * Log a debug message. Args are inserted into the message using the {} pattern.
     */
    protected abstract void debug(Class<?> from, String message, Object... args);

    public static synchronized int getLevel() {
        return level;
    }

    public static synchronized void setLevel(int level) {
        if (level != LoggerFacade.level) {
            LoggerFacade.level = level;
        }
    }

    /**
     * Standard implementation for formatting the message with args.
     */
    static String formatMsg(Class<?> from, String message, Object... args) {
        for (Object arg : args) {
            if (arg == null) {
                arg = "<null>";
            }
            int nextSlot = message.indexOf("{}");
            if (nextSlot != -1) {
                if (nextSlot == 0) {
                    message = arg.toString() + message.substring(2);
                } else {
                    message = message.substring(0, nextSlot) + arg.toString() + message.substring(nextSlot + 2);
                }
            } else {
                break;
            }
        }
        return "[" + from.getName() + "] " + message;
    }

}
