package com.salesforce.bazel.eclipse.core.model.execution;

import com.salesforce.bazel.sdk.command.BazelCommand;

/**
 * Extracts task name for progress reporting with a workaround for
 * https://github.com/eclipse-platform/eclipse.platform.ui/issues/1112
 */
class TaskNameHelper {

    static final int TASK_LENGTH_LIMIT = 120;

    static String getSpanName(BazelCommand<?> command) {
        return command.getPurpose();
    }

    static String getTaskName(BazelCommand<?> command) {
        var taskName = command.toString();
        if (taskName.length() <= TASK_LENGTH_LIMIT) {
            return taskName;
        }

        // see https://github.com/eclipse-platform/eclipse.platform.ui/issues/1112 why we should limit the length on task name
        return taskName.substring(0, TASK_LENGTH_LIMIT - 3) + "...";
    }
}
