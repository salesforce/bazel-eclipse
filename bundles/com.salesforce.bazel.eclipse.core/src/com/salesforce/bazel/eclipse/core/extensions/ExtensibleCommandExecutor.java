package com.salesforce.bazel.eclipse.core.extensions;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelCommandExecutor;

/**
 * A {@link BazelCommandExecutor} implementation using a delegate provided by the Eclipse extension registry.
 */
public final class ExtensibleCommandExecutor implements BazelCommandExecutor {

    private static Logger LOG = LoggerFactory.getLogger(ExtensibleCommandExecutor.class);

    private static final String EXTENSION_POINT_COMMAND_EXECUTOR = "com.salesforce.bazel.eclipse.core.executor";
    private static final String ATTR_CLASS = "class";

    private volatile BazelCommandExecutor delegate;

    BazelCommandExecutor ensureDelegate() throws IOException {
        try {
            var commandExecutor = delegate;
            while (commandExecutor == null) {
                commandExecutor = delegate = findCommandExecutor();
            }
            return commandExecutor;
        } catch (CoreException e) {
            LOG.debug("Error creating command executor.", e);
            throw new IOException("Error creating command executor!", e);
        }
    }

    @Override
    public <R> R execute(BazelCommand<R> command, CancelationCallback cancellationCallback) throws IOException {
        return ensureDelegate().execute(command, cancellationCallback);
    }

    private BazelCommandExecutor findCommandExecutor() throws CoreException {
        var elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_COMMAND_EXECUTOR);
        if (elements.length == 0) {
            throw new IllegalStateException("No suitable extensions available providing a command executor!");
        }

        Arrays.sort(elements, new PriorityAttributeComparator());

        return requireNonNull((BazelCommandExecutor) elements[0].createExecutableExtension(ATTR_CLASS),
            "No object returned from extension factory");
    }

    @Override
    public BazelBinary getBazelBinary() throws NullPointerException {
        try {
            return ensureDelegate().getBazelBinary();
        } catch (IOException e) {
            throw new IllegalStateException("Error creating command executor!", e);
        }
    }
}