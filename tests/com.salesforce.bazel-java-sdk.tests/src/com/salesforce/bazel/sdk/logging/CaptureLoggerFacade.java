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

import java.util.function.Consumer;

public class CaptureLoggerFacade extends LoggerFacade {
    Consumer<LogEvent> consumer;

    public CaptureLoggerFacade(Consumer<LogEvent> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void error(Class<?> from, String message, Object... args) {
        consumer.accept(new LogEvent(LogType.ERROR, from, message, null, args));
    }

    @Override
    public void error(Class<?> from, String message, Throwable exception, Object... args) {
        consumer.accept(new LogEvent(LogType.ERROR, from, message, exception, args));
    }

    @Override
    public void warn(Class<?> from, String message, Object... args) {
        consumer.accept(new LogEvent(LogType.WARN, from, message, null, args));
    }

    @Override
    public void info(Class<?> from, String message, Object... args) {
        consumer.accept(new LogEvent(LogType.INFO, from, message, null, args));
    }

    @Override
    public void debug(Class<?> from, String message, Object... args) {
        consumer.accept(new LogEvent(LogType.DEBUG, from, message, null, args));
    }

    public static enum LogType {
        ERROR, WARN, INFO, DEBUG;
    }

    public static class LogEvent {
        public LogEvent(LogType type, Class<?> from, String message, Throwable exception, Object[] args) {
            this.type = type;
            this.from = from;
            this.message = message;
            this.exception = exception;
            this.args = args;
        }

        public final LogType type;
        public final Class<?> from;
        public final String message;
        public final Throwable exception;
        public final Object[] args;
    }

}
