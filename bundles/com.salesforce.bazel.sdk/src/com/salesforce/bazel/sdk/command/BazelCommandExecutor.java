package com.salesforce.bazel.sdk.command;

import java.io.IOException;

/**
 * A contract for executing {@link BazelCommand Bazel commands}.
 * <p>
 * The executor is responsible for selecting the proper Bazel binary as well as configuring the environment for Bazel
 * execution.
 * </p>
 */
public interface BazelCommandExecutor {

    /**
     * Functional interface to allow cancellation of an execution.
     */
    @FunctionalInterface
    interface CancelationCallback {
        boolean isCanceled();
    }

    /**
     * Executes the given command.
     * <p>
     * As part of the execution the command will be updated with more details of the execution (eg., full command line,
     * execution time, etc.). During execution the modifying the command by another thread is not allowed and will
     * likely result in unexpected behavior.
     * </p>
     *
     * @param <R>
     *            the result return type
     * @param command
     *            the command to execut
     * @param cancellationCallback
     *            callback to check whether an execution has been cancelled
     * @return the result
     */
    <R> R execute(BazelCommand<R> command, CancelationCallback cancellationCallback) throws IOException;

    /**
     * {@return the bazel binary used by the command executor}
     *
     * @throws NullPointerException
     *             if the command executor has no Bazel binary
     */
    BazelBinary getBazelBinary();

}
