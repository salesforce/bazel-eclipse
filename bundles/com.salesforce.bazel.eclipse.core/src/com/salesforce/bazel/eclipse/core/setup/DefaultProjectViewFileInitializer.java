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
package com.salesforce.bazel.eclipse.core.setup;

import static com.salesforce.bazel.eclipse.core.setup.ImportBazelWorkspaceJob.ECLIPSE_DEFAULTS_PROJECTVIEW;
import static java.lang.String.format;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.writeString;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Initializes a default <code>.bazelproject</code> file if none exists.
 */
public class DefaultProjectViewFileInitializer {

    private static final Path INTELLIJ_PROJECT_VIEW_TEMPLATE = Path.of("tools/intellij/.managed.bazelproject");

    private final Path workspaceRoot;

    /**
     * Initialize with a workspace root
     *
     * @param workspaceRoot
     *            the workspace root
     */
    public DefaultProjectViewFileInitializer(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Creates a project view file at the given location
     *
     * @param projectViewFile
     *            the file to create
     * @throws IOException
     *             if the file cannot be created (eg., because it already exists)
     */
    public void create(Path projectViewFile) throws IOException {
        if (!isDirectory(projectViewFile.getParent())) {
            createDirectories(projectViewFile.getParent());
        }

        List<String> lines = new ArrayList<>();

        // import default if exists
        var eclipseDefaults = workspaceRoot.resolve(ECLIPSE_DEFAULTS_PROJECTVIEW.toPath());
        if (isRegularFile(eclipseDefaults)) {
            lines.add("# import default settings (never remove this line 🙏)");
            lines.add(format("import %s", ECLIPSE_DEFAULTS_PROJECTVIEW));
        }

        // populate with template
        var templateFile = workspaceRoot.resolve(INTELLIJ_PROJECT_VIEW_TEMPLATE);
        if (isRegularFile(templateFile)) {
            lines.add("");
            lines.addAll(readAllLines(templateFile));
        } else {
            lines.add("""
                    # The project view file (.bazelproject) is used to import targets into the IDE.
                    #
                    # See: https://ij.bazel.build/docs/project-views.html
                    #
                    # This files provides a default experience for developers working with the project.
                    # You should customize it to suite your needs.

                    directories:
                      .  # import everything (remove the dot if this is too much)

                    derive_targets_from_directories: true
                    """);
        }

        writeString(
            projectViewFile,
            lines.stream().collect(joining(System.lineSeparator())),
            StandardOpenOption.CREATE_NEW);
    }

}
