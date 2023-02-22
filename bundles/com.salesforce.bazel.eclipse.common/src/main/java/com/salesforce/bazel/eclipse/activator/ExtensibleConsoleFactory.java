package com.salesforce.bazel.eclipse.activator;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;

import com.salesforce.bazel.sdk.console.CommandConsole;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;

public final class ExtensibleConsoleFactory implements CommandConsoleFactory {

    private volatile CommandConsoleFactory delegate;

    @Override
    public CommandConsole get(String name, String title) throws IOException {
        try {
            return ensureDelegate().get(name, title);
        } catch (CoreException | RuntimeException e) {
            throw new IOException("Error creating Console factory!", e);
        }
    }

    CommandConsoleFactory ensureDelegate() throws CoreException {
        CommandConsoleFactory consoleFactory = delegate;
        while (consoleFactory == null) {
            consoleFactory = delegate = findConsoleFactory();
        }
        return consoleFactory;
    }

    private CommandConsoleFactory findConsoleFactory() throws CoreException {
        IConfigurationElement[] elements =
                Platform.getExtensionRegistry().getConfigurationElementsFor("com.salesforce.bazel.consolefactory");
        if (elements.length == 0) {
            throw new IllegalStateException("No extensions available providing a Console Factory!");
        }

        Arrays.sort(elements, new Comparator<IConfigurationElement>() {
            @Override
            public int compare(IConfigurationElement o1, IConfigurationElement o2) {
                int p1 = safeParse(o1.getAttribute("priority"));
                int p2 = safeParse(o2.getAttribute("priority"));

                return p1 - p2;
            }

            private int safeParse(String priority) {
                try {
                    return priority != null ? Integer.parseInt(priority) : 1000;
                } catch (NumberFormatException e) {
                    return 1000;
                }
            }
        });

        return requireNonNull((CommandConsoleFactory) elements[0].createExecutableExtension("class"),
            "No object returned from extension factory");
    }
}