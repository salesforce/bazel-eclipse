package com.salesforce.bazel.eclipse.jdtls.logback;

import static ch.qos.logback.core.status.StatusUtil.filterStatusListByTimeThreshold;

import java.util.List;

import org.eclipse.core.runtime.ILog;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.helpers.ThrowableToStringArray;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.status.StatusUtil;
import ch.qos.logback.core.util.CachingDateFormatter;

public class StatusPrinter {

    static CachingDateFormatter cachingDateFormat = new CachingDateFormatter("HH:mm:ss,SSS");

    private static void appendThrowable(StringBuilder sb, Throwable t) {
        var stringRep = ThrowableToStringArray.convert(t);

        for (String s : stringRep) {
            if (s.startsWith(CoreConstants.CAUSED_BY)) {
                // nothing
            } else if (Character.isDigit(s.charAt(0))) {
                // if line resembles "48 common frames omitted"
                sb.append("\t... ");
            } else {
                // most of the time. just add a tab+"at"
                sb.append("\tat ");
            }
            sb.append(s).append(CoreConstants.LINE_SEPARATOR);
        }
    }

    public static void buildStr(StringBuilder sb, String indentation, Status s) {
        String prefix;
        if (s.hasChildren()) {
            prefix = indentation + "+ ";
        } else {
            prefix = indentation + "|-";
        }

        if (cachingDateFormat != null) {
            var dateStr = cachingDateFormat.format(s.getTimestamp());
            sb.append(dateStr).append(" ");
        }
        sb.append(prefix).append(s).append(CoreConstants.LINE_SEPARATOR);

        if (s.getThrowable() != null) {
            appendThrowable(sb, s.getThrowable());
        }
        if (s.hasChildren()) {
            var ite = s.iterator();
            while (ite.hasNext()) {
                var child = ite.next();
                buildStr(sb, indentation + "  ", child);
            }
        }
    }

    private static void buildStrFromStatusList(StringBuilder sb, List<Status> statusList) {
        if (statusList == null) {
            return;
        }
        for (Status s : statusList) {
            buildStr(sb, "", s);
        }
    }

    public static void logInCaseOfErrorsOrWarnings(LoggerContext context, ILog log) {
        if (context == null) {
            throw new IllegalArgumentException("Context argument cannot be null");
        }

        var sm = context.getStatusManager();
        if (sm == null) {
            log.warn("WARN: Context named \"" + context.getName() + "\" has no status manager");
        } else {
            var statusUtil = new StatusUtil(context);
            var threshold = 0L;
            if (statusUtil.getHighestLevel(threshold) >= Status.WARN) {
                print(sm, threshold, log);
            }
        }
    }

    public static void print(StatusManager sm, long threshold, ILog log) {
        var sb = new StringBuilder();
        var filteredList = filterStatusListByTimeThreshold(sm.getCopyOfStatusList(), threshold);
        buildStrFromStatusList(sb, filteredList);
        log.info(sb.toString());
    }
}
