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
import java.util.Optional;

import net.starlark.java.syntax.CallExpression;
import net.starlark.java.syntax.ExpressionStatement;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.Identifier;
import net.starlark.java.syntax.LoadStatement;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.StarlarkFile;

/**
 * A somewhat generic reader for Bazel Starlark files (eg., <code>BUILD.bazel</code>, <code>WORKSPACE.bazel</code> and
 * <code>MODULE.bazel</code>).
 * <p>
 * This class is not thread-safe.
 * </p>
 */
public class BazelStarlarkFileReader {

    private final Path buildFile;

    private List<LoadStatement> loadStatements;
    private List<CallExpression> functionCalls;
    private Optional<CallExpression> packageCall;
    private Optional<CallExpression> moduleCall;

    private StarlarkFile file;

    public BazelStarlarkFileReader(Path buildFile) {
        this.buildFile = buildFile;
    }

    /**
     * Returns a list of all load statements defined in the build file.
     * <p>
     * The returned list should not be modified.
     * </p>
     *
     * @return a list of all load statements defined in the build file (never <code>null</code> after {@link #read()})
     */
    public List<LoadStatement> getLoadStatements() {
        return requireNonNull(loadStatements, "no load statements initialized; did you forget to call #read?");
    }

    /**
     * Returns a list of all macro calls defined in the build file.
     * <p>
     * A macro call is a top-level {@link CallExpression} with {@link CallExpression#getFunction()} is an instance of
     * {@link Identifier}.
     * </p>
     * <p>
     * The returned list should not be modified.
     * </p>
     *
     * @return a list of all load statements defined in the build file (never <code>null</code> after {@link #read()})
     */
    public List<CallExpression> getMacroCalls() {
        return requireNonNull(functionCalls, "no macro calls initialized; did you forget to call #read?");
    }

    /**
     * {@return the first <code>module</code> function call found in this build file (maybe <code>null</code> if none
     * was found)}
     */
    public CallExpression getModuleCall() {
        Optional<CallExpression> moduleCall =
                requireNonNull(this.moduleCall, "no module call initialized; did you forget to call #read?");
        return moduleCall.isPresent() ? moduleCall.get() : null;
    }

    /**
     * {@return the first <code>package</code> function call found in this build file (maybe <code>null</code> if none
     * was found)}
     */
    public CallExpression getPackageCall() {
        Optional<CallExpression> packageCall =
                requireNonNull(this.packageCall, "no package call initialized; did you forget to call #read?");
        return packageCall.isPresent() ? packageCall.get() : null;
    }

    /**
     * Reads the given build file and collects information.
     * <p>
     * Should not be called more than once.
     * </p>
     *
     * @throws IOException
     */
    public void read() throws IOException {
        if (file != null) {
            throw new IllegalStateException("#read must not be called multiple times!");
        }

        // read the file (using UTF-8)
        var input = ParserInput.readFile(buildFile.toString());

        // options for processing BUILD files as per https://github.com/bazelbuild/bazel/blob/f35132cea9703a14592d732e00197bf03fb91be5/src/main/java/com/google/devtools/build/lib/skyframe/PackageFunction.java#L1477
        var options = FileOptions.builder()
                .requireLoadStatementsFirst(false)
                .loadBindsGlobally(true)
                .allowToplevelRebinding(true)
                .build();

        file = StarlarkFile.parse(input, options);
        if (!file.ok()) {
            throw new IOException(
                    "Syntax errors in file '" + buildFile + "': "
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

        functionCalls = file.getStatements()
                .stream()
                .filter(ExpressionStatement.class::isInstance)
                .map(ExpressionStatement.class::cast)
                .filter(e -> e.getExpression() instanceof CallExpression)
                .map(e -> (CallExpression) e.getExpression())
                .filter(c -> (c.getFunction() instanceof Identifier))
                .collect(toList());

        packageCall = functionCalls.stream()
                .filter(c -> ((Identifier) c.getFunction()).getName().equals("package"))
                .findFirst();

        moduleCall = functionCalls.stream()
                .filter(c -> ((Identifier) c.getFunction()).getName().equals("module"))
                .findFirst();
    }

}
