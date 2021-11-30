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
 * Helper to log messages. Doesn't cache anything but class name for common logging frameworks. This allows the
 * LoggerFacade to be changed and without having to constantly give the class.
 * <p>
 * This is the preferred way to log in the Bazel SDK.
 */
public final class LogHelper {
    private final Class<?> from;

    /**
     * Get the Logger for the <i>from</i> class.
     */
    public static LogHelper log(Class<?> from) {
        return new LogHelper(from);
    }

    private LogHelper(Class<?> from) {
        this.from = from;
    }

    /**
     * Gets the active log level for this LogHelper.
     * <p>
     * Levels: 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR
     */
    public int getLevel() {
        return LoggerFacade.getLevel();
    }

    public void setLevel(int level) {
        LoggerFacade.setLevel(level);
    }

    public boolean isDebugLevel() {
        return LoggerFacade.DEBUG == LoggerFacade.getLevel();
    }

    public boolean isInfoLevel() {
        return LoggerFacade.INFO == LoggerFacade.getLevel();
    }

    public boolean isWarnLevel() {
        return LoggerFacade.WARN == LoggerFacade.getLevel();
    }

    public boolean isErrorLevel() {
        return LoggerFacade.ERROR == LoggerFacade.getLevel();
    }

    /**
     * Log an error message. Args are inserted into the message using the {} pattern.
     */
    public void error(String message, Object... args) {
        getFacade().log(LoggerFacade.ERROR, from, message, args);
    }

    /**
     * Log an error message. Args are inserted into the message using the {} pattern.
     */
    public void error(String message, Throwable exception, Object... args) {
        getFacade().log(LoggerFacade.ERROR, from, message, exception, args);
    }

    /**
     * Log a warning message. Args are inserted into the message using the {} pattern.
     */
    public void warn(String message, Object... args) {
        getFacade().log(LoggerFacade.WARN, from, message, args);
    }

    /**
     * Log an info message. Args are inserted into the message using the {} pattern.
     */
    public void info(String message, Object... args) {
        getFacade().log(LoggerFacade.INFO, from, message, args);
    }

    /**
     * Log a debug message. Args are inserted into the message using the {} pattern.
     */
    public void debug(String message, Object... args) {
        getFacade().log(LoggerFacade.DEBUG, from, message, args);
    }

    /**
     * Log a debug message. Args are inserted into the message using the {} pattern.
     */
    public void log(int level, String message, Object... args) {
        getFacade().log(level, from, message, args);
    }

    private static LoggerFacade getFacade() {
        return LoggerFacade.instance();
    }
}
