package com.salesforce.bazel.eclipse.core.extensions;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelCommandExecutor;

/**
 * A {@link BazelCommandExecutor} implementation using a delegate provided by the Eclipse extension registry.
 */
public final class ExtensibleCommandExecutor implements BazelCommandExecutor {

    private static final String EXTENSION_POINT_COMMAND_EXECUTOR = "com.salesforce.bazel.eclipse.core.executor";
    private static final String ATTR_CLASS = "class";
    private static final String ATTR_PRIORITY = "priority";

    private volatile BazelCommandExecutor delegate;

    BazelCommandExecutor ensureDelegate() throws CoreException {
        var commandExecutor = delegate;
        while (commandExecutor == null) {
            commandExecutor = delegate = findCommandExecutor();
        }
        return commandExecutor;
    }

    @Override
    public <R> R execute(BazelCommand<R> command, CancelationCallback cancellationCallback) throws IOException {
        try {
            return ensureDelegate().execute(command, cancellationCallback);
        } catch (CoreException | RuntimeException e) {
            throw new IOException("Error creating command executor!", e);
        }
    }

    private BazelCommandExecutor findCommandExecutor() throws CoreException {
        var elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_COMMAND_EXECUTOR);
        if (elements.length == 0) {
            throw new IllegalStateException("No suitable extensions available providing a command executor!");
        }

        Arrays.sort(elements, new Comparator<IConfigurationElement>() {
            @Override
            public int compare(IConfigurationElement o1, IConfigurationElement o2) {
                var p1 = safeParse(o1.getAttribute(ATTR_PRIORITY));
                var p2 = safeParse(o2.getAttribute(ATTR_PRIORITY));

                return p2 - p1; // higher priority is more important
            }

            private int safeParse(String priority) {
                try {
                    return priority != null ? Integer.parseInt(priority) : 10;
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        });

        return requireNonNull((BazelCommandExecutor) elements[0].createExecutableExtension(ATTR_CLASS),
            "No object returned from extension factory");
    }

    @Override
    public BazelBinary getBazelBinary() throws NullPointerException {
        try {
            return ensureDelegate().getBazelBinary();
        } catch (CoreException e) {
            throw new IllegalStateException("Error creating command executor!", e);
        }
    }
}