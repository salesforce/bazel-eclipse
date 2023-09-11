package com.salesforce.bazel.sdk.command;

import java.io.IOException;
import java.io.OutputStream;

import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor.PreparedCommandLine;

/**
 * A provider for process out and error streams.
 * <p>
 * Used by
 * {@link DefaultBazelCommandExecutor#doExecuteProcess(BazelCommand, com.salesforce.bazel.sdk.command.BazelCommandExecutor.CancelationCallback, ProcessBuilder, PreparedCommandLine)}
 * implementation to requests streams when necessary and print additional execution details.
 * </p>
 */
public class ProcessStreamsProvider implements AutoCloseable {

    /**
     * Hook called when the process is about to be started.
     * <p>
     * Implementors can use this to collect/print additional details.
     * </p>
     */
    public void beginExecution() {
        // empty
    }

    /**
     * Called by <code>try-with-resource</code> block to close/release any underlying streams.
     * <p>
     * Note, as the defaults in {@link #getErrorStream()} and {@link #getOutStream()} use streams which shouldn't be
     * closed, the default implementation does nothing. Subclasses creating their own streams must override this
     * method and close any resources.
     * </p>
     */
    @Override
    public void close() throws IOException {
        // nothing to close, i.e. System.err and System.out remain open
    }

    /**
     * Hook called when the process execution was canceled/aborted on user request.
     * <p>
     * Implementors can use this to collect/print additional details.
     * </p>
     */
    public void executionCanceled() {
        // empty
    }

    /**
     * Hook called when the process execution failse.
     * <p>
     * Implementors can use this to collect/print additional details. They may also wrap/enrich the
     * {@link IOException} and throw a more detailed one.
     * </p>
     *
     * @param cause
     *            the {@link IOException} to be thrown after this method returns
     * @throws IOException
     *             with additional information
     */
    public void executionFailed(IOException cause) throws IOException {
        // empty
    }

    /**
     * Hook called when the process execution finished.
     * <p>
     * Implementors can use this to collect/print additional details.
     * </p>
     *
     * @param exitCode
     *            the process exit code
     */
    public void executionFinished(int exitCode) {
        // empty
    }

    /**
     * Returns the output stream to be used for process <code>STDERR</code>.
     * <p>
     * Must return the same output stream instance every time.
     * </p>
     * <p>
     * Note, the returned stream will never be closed by the caller. Implementors are responsible for closing when
     * {@link #close()} is called.
     * </p>
     *
     *
     * @return the output stream to be used for process <code>STDERR</code>, defaults to {@link System#err}
     */
    public OutputStream getErrorStream() {
        return System.err;
    }

    /**
     * Returns the output stream to be used for process <code>STDOUT</code>.
     * <p>
     * Note, this method is only called when the command is <b>not</b> redirecting its output into a file.
     * </p>
     * <p>
     * Must return the same output stream instance every time.
     * </p>
     * <p>
     * Note, the returned stream will never be closed by the caller. Implementors are responsible for closing when
     * {@link #close()} is called.
     * </p>
     *
     * @return the output stream to be used for process <code>STDOUT</code>, defaults to {@link System#out}
     */
    public OutputStream getOutStream() {
        return System.out;
    }
}