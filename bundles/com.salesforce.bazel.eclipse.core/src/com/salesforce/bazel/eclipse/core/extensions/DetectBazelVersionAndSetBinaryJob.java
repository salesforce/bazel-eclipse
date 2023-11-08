package com.salesforce.bazel.eclipse.core.extensions;

import static java.lang.String.format;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.BazelBinaryVersionDetector;

/**
 * Calls <code>bazel --version</code> on a provided binary to identify the version use.
 */
public final class DetectBazelVersionAndSetBinaryJob extends Job {
    private final Path binary;
    private final boolean wrapExecutionIntoShell;
    private final Consumer<BazelBinary> binaryConsumer;
    private final Supplier<BazelBinary> fallbackSupplier;

    /**
     * @param binary
     *            the binary to test
     * @param wrapExecutionIntoShell
     *            <code>true</code> if shell wrapping is desired, <code>false</code> otherwise
     * @param binaryConsumer
     *            receiver of the binary including the detected version (will be called with a fallback value in case of
     *            errors)
     * @param fallbackSupplier
     *            supplier for fallback value in case of errors
     */
    public DetectBazelVersionAndSetBinaryJob(Path binary, boolean wrapExecutionIntoShell,
            Consumer<BazelBinary> binaryConsumer, Supplier<BazelBinary> fallbackSupplier) {
        super(format("Detecting Bazel version...", binary));
        this.binary = binary;
        this.wrapExecutionIntoShell = wrapExecutionIntoShell;
        this.binaryConsumer = binaryConsumer;
        this.fallbackSupplier = fallbackSupplier;
        setSystem(true);
        setPriority(SHORT);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        try {
            var bazelVersion = new BazelBinaryVersionDetector(binary, wrapExecutionIntoShell).detectVersion();
            binaryConsumer.accept(new BazelBinary(binary, bazelVersion));
            return Status.OK_STATUS;
        } catch (IOException e) {
            binaryConsumer.accept(fallbackSupplier.get());
            return Status.error(format("Unable to detect Bazel version of binary '%s'!", binary), e);
        } catch (InterruptedException e) {
            EclipseHeadlessBazelCommandExecutor.LOG
                    .warn("Interrupted waiting for bazel --version to respond for binary '{}'", binary, e);
            binaryConsumer.accept(fallbackSupplier.get());
            return Status.CANCEL_STATUS;
        }
    }
}