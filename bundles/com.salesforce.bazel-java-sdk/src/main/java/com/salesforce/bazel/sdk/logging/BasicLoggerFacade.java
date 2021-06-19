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

// we will revisit this later, see https://github.com/salesforce/bazel-eclipse/issues/10
// import org.slf4j.LoggerFactory;

/**
 * Default facade that logs to stdout/stderr.
 * <p>
 * You can set the level: 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR
 */
public class BasicLoggerFacade extends LoggerFacade {

    @Override
    public void error(Class<?> from, String message, Object... args) {
        // LoggerFactory.getLogger(from).error(message, args);
        System.err.println("ERROR " + formatMsg(from, message, args));
    }

    /**
     * Log an error message. Args are inserted into the message using the {} pattern.
     */
    @Override
    public void error(Class<?> from, String message, Throwable exception, Object... args) {
        exception.printStackTrace();
        System.err.println("ERROR " + formatMsg(from, message, args));
    }

    /**
     * Log a warning message. Args are inserted into the message using the {} pattern.
     */
    @Override
    public void warn(Class<?> from, String message, Object... args) {
        if (level <= WARN) {
            System.err.println("WARN " + formatMsg(from, message, args));
        }
    }

    /**
     * Log an info message. Args are inserted into the message using the {} pattern.
     */
    @Override
    public void info(Class<?> from, String message, Object... args) {
        if (level <= INFO) {
            System.out.println(formatMsg(from, message, args));
        }
    }

    /**
     * Log a debug message. Args are inserted into the message using the {} pattern.
     */
    @Override
    public void debug(Class<?> from, String message, Object... args) {
        if (level == DEBUG) {
            System.out.println(formatMsg(from, message, args));
        }
    }
}
