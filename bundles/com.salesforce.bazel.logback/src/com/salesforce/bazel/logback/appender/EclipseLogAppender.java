/*-
 * Copyright (c) 2010, 2022 Sonatype, Inc.
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
 *      Salesforce - Aadapted fo Bazel Eclipse Feature
 */
package com.salesforce.bazel.logback.appender;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

public class EclipseLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private static final String BUNDLE_ID = "com.salesforce.bazel.logback"; //$NON-NLS-1$
    private static final ILog ECLIPSE_LOG = Platform.getLog(EclipseLogAppender.class);

    private static Throwable getThrowable(ILoggingEvent logEvent) {
        if (logEvent.getThrowableProxy() instanceof ThrowableProxy proxy) {
            return proxy.getThrowable();
        }
        var args = logEvent.getArgumentArray();
        return (args != null) && (args.length > 0) && args[args.length - 1] instanceof Throwable throwable ? throwable
                : null;
    }

    @Override
    protected void append(ILoggingEvent logEvent) {
        var severity = switch (logEvent.getLevel().levelInt) {
            case Level.ERROR_INT -> IStatus.ERROR;
            case Level.WARN_INT -> IStatus.WARNING;
            case Level.INFO_INT -> IStatus.INFO;
            default -> -1;
        };
        if (severity != -1) {
            IStatus status =
                    new Status(severity, BUNDLE_ID, logEvent.getFormattedMessage().strip(), getThrowable(logEvent));
            ECLIPSE_LOG.log(status);
        }
    }
}
