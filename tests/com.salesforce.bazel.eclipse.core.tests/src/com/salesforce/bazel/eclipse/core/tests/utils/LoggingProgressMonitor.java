package com.salesforce.bazel.eclipse.core.tests.utils;

import static java.lang.String.format;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggingProgressMonitor extends NullProgressMonitor {
    private final Logger log;
    private final Job job;
    private final String prefix;
    private String name;

    public LoggingProgressMonitor(Job job) {
        this.job = job;
        this.log = LoggerFactory.getLogger(job.getClass());
        this.prefix = format("[%s] ", job.getName());
    }

    @Override
    public void beginTask(String name, int totalWork) {
        setTaskName(name);
    }

    @Override
    public void setTaskName(String name) {
        this.name = name;
        if ((name != null) && !name.isBlank()) {
            log("{}", name);
        }
    }

    @Override
    public void subTask(String name) {
        if ((name != null) && !name.isBlank()) {
            var taskName = this.name;
            if ((taskName != null) && !taskName.isBlank()) {
                log("{} - {}", taskName, name);
            } else {
                log("{}", name);
            }
        }
    }

    private void log(String msg, Object... arguments) {
        if (job.isSystem()) {
            if (log.isDebugEnabled()) {
                log.debug(prefix + msg, arguments);
            }
        } else if (log.isInfoEnabled()) {
            log.info(prefix + msg, arguments);
        }

    }
}