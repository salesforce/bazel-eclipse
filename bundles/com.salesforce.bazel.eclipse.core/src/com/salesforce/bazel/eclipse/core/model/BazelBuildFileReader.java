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

package com.salesforce.bazel.eclipse.core.model;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import net.starlark.java.syntax.CallExpression;
import net.starlark.java.syntax.ExpressionStatement;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.Identifier;
import net.starlark.java.syntax.LoadStatement;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.StarlarkFile;

/**
 * A reader for <code>BUILD</code> files.
 */
public class BazelBuildFileReader {

    private final Path buildFile;

    private List<LoadStatement> loadStatements;
    private List<CallExpression> macroCalls;

    public BazelBuildFileReader(Path buildFile) {
        this.buildFile = buildFile;
    }

    public List<LoadStatement> getLoadStatements() {
        return requireNonNull(loadStatements, "no load statements initialized; did you forget to call #read?");
    }

    public List<CallExpression> getMacroCalls() {
        return requireNonNull(macroCalls, "no macro calls initialized; did you forget to call #read?");
    }

    public void read() throws IOException {
        // read the file (using UTF-8)
        var input = ParserInput.readFile(buildFile.toString());

        // options for processing BUILD files as per https://github.com/bazelbuild/bazel/blob/f35132cea9703a14592d732e00197bf03fb91be5/src/main/java/com/google/devtools/build/lib/skyframe/PackageFunction.java#L1477
        var options = FileOptions.builder()
                .requireLoadStatementsFirst(false)
                .loadBindsGlobally(true)
                .allowToplevelRebinding(true)
                .build();

        var file = StarlarkFile.parse(input, options);
        if (!file.ok()) {
            throw new IOException("Syntax errors in file '" + buildFile + "': "
                    + file.errors()
                            .stream()
                            .map(e -> e.message() + " (" + e.location().toString() + ")")
                            .collect(joining(System.lineSeparator())));
        }

        // note, we do not perform any additional checks or compilation here ... we are only interested in top-level loads and macro calls

        // read load statements
        loadStatements = file.getStatements()
                .stream()
                .filter(LoadStatement.class::isInstance)
                .map(LoadStatement.class::cast)
                .collect(toList());

        macroCalls = file.getStatements()
                .stream()
                .filter(ExpressionStatement.class::isInstance)
                .map(ExpressionStatement.class::cast)
                .filter(e -> e.getExpression() instanceof CallExpression)
                .map(e -> (CallExpression) e.getExpression())
                .filter(c -> (c.getFunction() instanceof Identifier))
                .collect(toList());
    }

}
