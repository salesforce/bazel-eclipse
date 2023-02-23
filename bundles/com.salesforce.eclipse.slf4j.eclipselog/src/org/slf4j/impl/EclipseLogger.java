/*-
 * Copyright (c) 2017 Salesforce and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Salesforce - initial API and implementation
 */
package org.slf4j.impl;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

public class EclipseLogger extends MarkerIgnoringBase {

    /** serialVersionUID */
    private static final long serialVersionUID = -1L;

    private final ILog eclipseLog;
    private final boolean sendInfoToEclipseLog;
    private final boolean isDebugEnabled;
    private final boolean isTraceEnabled;

    public EclipseLogger(final String name, final Bundle bundle) {
        eclipseLog = Platform.getLog(bundle);
        isDebugEnabled = "true".equalsIgnoreCase(Platform.getDebugOption(name + "/debug"));
        isTraceEnabled = "true".equalsIgnoreCase(Platform.getDebugOption(name + "/trace"));
        sendInfoToEclipseLog = Boolean.getBoolean("slf4j.binding.eclipselog.includeInfo");
    }

    @Override
    public void debug(final String msg) {
        if (isDebugEnabled) {
            logToSysOut("DEBUG", msg);
        }
    }

    @Override
    public void debug(final String format, final Object arg) {
        if (isDebugEnabled) {
            logToSysOut("DEBUG", MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        if (isDebugEnabled) {
            logToSysOut("DEBUG", MessageFormatter.format(format, arguments));
        }
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        if (isDebugEnabled) {
            logToSysOut("DEBUG", MessageFormatter.format(format, arg1, arg2));
        }
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        if (isDebugEnabled) {
            logToSysOut("DEBUG", msg, t);
        }
    }

    @Override
    public void error(final String msg) {
        if (isDebugEnabled) {
            logToSysOut("ERROR", msg, null);
        }
        logToEclipse(IStatus.ERROR, msg, null);
    }

    @Override
    public void error(final String format, final Object arg) {
        if (isDebugEnabled) {
            logToSysOut("ERROR", MessageFormatter.format(format, arg));
        }
        logToEclipse(IStatus.ERROR, MessageFormatter.format(format, arg));
    }

    @Override
    public void error(final String format, final Object... arguments) {
        if (isDebugEnabled) {
            logToSysOut("ERROR", MessageFormatter.format(format, arguments));
        }
        logToEclipse(IStatus.ERROR, MessageFormatter.format(format, arguments));
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        if (isDebugEnabled) {
            logToSysOut("ERROR", MessageFormatter.format(format, arg1, arg2));
        }
        logToEclipse(IStatus.ERROR, MessageFormatter.format(format, arg1, arg2));
    }

    @Override
    public void error(final String msg, final Throwable t) {
        if (isDebugEnabled) {
            logToSysOut("ERROR", msg, t);
        }
        logToEclipse(IStatus.ERROR, msg, t);
    }

    @Override
    public void info(final String msg) {
        if (isDebugEnabled) {
            logToSysOut("INFO", msg);
        }
        if (sendInfoToEclipseLog) {
            logToEclipse(IStatus.INFO, msg, null);
        }
    }

    @Override
    public void info(final String format, final Object arg) {
        if (isDebugEnabled) {
            logToSysOut("INFO", MessageFormatter.format(format, arg));
        }
        if (sendInfoToEclipseLog) {
            logToEclipse(IStatus.INFO, MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void info(final String format, final Object... arguments) {
        if (isDebugEnabled) {
            logToSysOut("INFO", MessageFormatter.format(format, arguments));
        }
        if (sendInfoToEclipseLog) {
            logToEclipse(IStatus.INFO, MessageFormatter.format(format, arguments));
        }
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        if (isDebugEnabled) {
            logToSysOut("INFO", MessageFormatter.format(format, arg1, arg2));
        }
        if (sendInfoToEclipseLog) {
            logToEclipse(IStatus.INFO, MessageFormatter.format(format, arg1, arg2));
        }
    }

    @Override
    public void info(final String msg, final Throwable t) {
        if (isDebugEnabled) {
            logToSysOut("INFO", msg, t);
        }
        if (sendInfoToEclipseLog) {
            logToEclipse(IStatus.INFO, msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return isDebugEnabled;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isTraceEnabled() {
        return isTraceEnabled;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    private void logToEclipse(final int severity, final FormattingTuple formattingTuple) {
        logToEclipse(severity, formattingTuple.getMessage(), formattingTuple.getThrowable());
    }

    private void logToEclipse(final int severity, final String msg, final Throwable t) {
        eclipseLog.log(new Status(severity, name, msg, t));
    }

    private void logToSysOut(final String logLevel, final FormattingTuple formattingTuple) {
        logToSysOut(logLevel, formattingTuple.getMessage(), formattingTuple.getThrowable());
    }

    private void logToSysOut(final String logLevel, final String msg) {
        System.out.printf("%1$TT.%1$TL [%s] [%s] [%s] %s%n", System.currentTimeMillis(),
            Thread.currentThread().getName(), logLevel, name, msg);
    }

    private void logToSysOut(final String logLevel, final String msg, final Throwable throwable) {
        logToSysOut(logLevel, msg);
        if (throwable != null) {
            throwable.printStackTrace(System.out);
        }
    }

    @Override
    public void trace(final String msg) {
        if (isTraceEnabled) {
            logToSysOut("TRACE", msg);
        }
    }

    @Override
    public void trace(final String format, final Object arg) {
        if (isTraceEnabled) {
            logToSysOut("TRACE", MessageFormatter.format(format, arg));
        }
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        if (isTraceEnabled) {
            logToSysOut("TRACE", MessageFormatter.format(format, arguments));
        }
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        if (isTraceEnabled) {
            logToSysOut("TRACE", MessageFormatter.format(format, arg1, arg2));
        }
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        if (isTraceEnabled) {
            logToSysOut("TRACE", msg, t);
        }
    }

    @Override
    public void warn(final String msg) {
        if (isDebugEnabled) {
            logToSysOut("WARN", msg, null);
        }
        logToEclipse(IStatus.WARNING, msg, null);
    }

    @Override
    public void warn(final String format, final Object arg) {
        if (isDebugEnabled) {
            logToSysOut("WARN", MessageFormatter.format(format, arg));
        }
        logToEclipse(IStatus.WARNING, MessageFormatter.format(format, arg));
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        if (isDebugEnabled) {
            logToSysOut("WARN", MessageFormatter.format(format, arguments));
        }
        logToEclipse(IStatus.WARNING, MessageFormatter.format(format, arguments));
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        if (isDebugEnabled) {
            logToSysOut("WARN", MessageFormatter.format(format, arg1, arg2));
        }
        logToEclipse(IStatus.WARNING, MessageFormatter.format(format, arg1, arg2));
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        if (isDebugEnabled) {
            logToSysOut("WARN", msg, t);
        }
        logToEclipse(IStatus.WARNING, msg, t);
    }

}