/*-
 * Copyright (c) 2024 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.eclipse.ui.execution;

import static java.lang.String.format;

import java.nio.file.Path;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.salesforce.bazel.eclipse.ui.BazelUIPlugin;

/**
 * A wrapper for managing the console per workspace
 */
public class BazelWorkspaceConsole {

    private final MessageConsole console;

    public BazelWorkspaceConsole(IPath workspaceLocation) {
        this(workspaceLocation.toOSString());
    }

    public BazelWorkspaceConsole(Path workspaceLocation) {
        this(workspaceLocation.toString());
    }

    public BazelWorkspaceConsole(String workspaceLocation) {
        console = findConsole(format("Bazel Workspace (%s)", workspaceLocation));
    }

    private MessageConsole findConsole(final String consoleName) {
        final var consoleManager = ConsolePlugin.getDefault().getConsoleManager();
        for (final IConsole existing : consoleManager.getConsoles()) {
            if (consoleName.equals(existing.getName())) {
                return (MessageConsole) existing;
            }
        }

        // no console found, so create a new one
        final var console = new MessageConsole(consoleName, getImageDescriptoForConsole());
        consoleManager.addConsoles(
            new IConsole[] {
                    console });
        return console;
    }

    private ImageDescriptor getImageDescriptoForConsole() {
        return BazelUIPlugin.getDefault().getImageRegistry().getDescriptor(BazelUIPlugin.ICON_BAZEL);
    }

    public MessageConsoleStream newMessageStream() {
        return console.newMessageStream();
    }

    public void show() {
        showConsole(console);
    }

    private void showConsole(MessageConsole console) {
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
    }

}
