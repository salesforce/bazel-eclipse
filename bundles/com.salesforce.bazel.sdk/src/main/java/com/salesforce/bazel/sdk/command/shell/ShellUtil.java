/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - initial implementation
*/
package com.salesforce.bazel.sdk.command.shell;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.salesforce.bazel.sdk.util.SystemUtil;

/**
 * Utility for working with OS shells
 */
public class ShellUtil {

    private volatile Path detectedShell;

    /**
     * Detects the login shell.
     * <p>
     * The detected shell value is cached for the duration of the lifetime of the {@link ShellUtil} instance. It's
     * assumed and expected that users changing their login shell restart their computer.
     * </p>
     *
     * @return the detected login shell (<code>null</code> on Windows)
     * @throws IOException
     */
    public Path detectLoginShell() throws IOException {
        if (getSystemUtil().isWindows()) {
            return null; // not supported
        }

        var shell = detectedShell;
        if (shell != null) {
            return shell;
        }

        synchronized (this) {
            if (getSystemUtil().isMac()) {
                return detectedShell = new MacOsLoginShellFinder().detectLoginShell();
            }
            if (getSystemUtil().isUnix()) {
                return detectedShell = new UnixLoginShellFinder().detectLoginShell();
            }
            throw new IOException("Unsupported OS: " + getSystemUtil().getOs());
        }
    }

    SystemUtil getSystemUtil() {
        return SystemUtil.getInstance();
    }

    protected String toQuotedStringForShell(List<String> commandLine) {
        var result = new StringBuilder();
        for (String arg : commandLine) {
            if (result.length() > 0) {
                result.append(' ');
            }
            var quoteArg = (arg.indexOf(' ') > -1) && !arg.startsWith("\\\"");
            if (quoteArg) {
                result.append("\"");
            }
            result.append(arg.replace("\"", "\\\""));
            if (quoteArg) {
                result.append("\"");
            }
        }
        return result.toString();
    }

    /**
     * Wraps the given command line into a command line for with shell execution.
     * <p>
     * Calls {@link #detectLoginShell()} to discover the login shell.
     * </p>
     * <p>
     * The command is returned as is if there is no login shell.
     * </p>
     *
     * @param commandLine
     *            the command to wrap
     * @return the wrapped command
     * @throws IOException
     */
    public List<String> wrapExecutionIntoShell(List<String> commandLine) throws IOException {
        var shell = detectLoginShell();
        if (shell != null) {
            return switch (shell.getFileName().toString()) {
                case "fish", "zsh", "bash" -> getSystemUtil().isMac() // login shell on Mac
                        ? List.of(shell.toString(), "-l", "-c", toQuotedStringForShell(commandLine))
                        : List.of(shell.toString(), "-c", toQuotedStringForShell(commandLine));
                default -> throw new IOException("Unsupported shell: " + shell);
            };
        }
        throw new IOException("Unable to wrap in shell. None detected!");
    }

}
