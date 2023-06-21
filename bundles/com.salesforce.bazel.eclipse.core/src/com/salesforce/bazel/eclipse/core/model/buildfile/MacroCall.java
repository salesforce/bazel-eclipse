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
package com.salesforce.bazel.eclipse.core.model.buildfile;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.salesforce.bazel.eclipse.core.model.BazelBuildFile;

import net.starlark.java.syntax.Argument;
import net.starlark.java.syntax.CallExpression;
import net.starlark.java.syntax.Expression;
import net.starlark.java.syntax.Identifier;
import net.starlark.java.syntax.ListExpression;
import net.starlark.java.syntax.NodeVisitor;
import net.starlark.java.syntax.StringLiteral;

/**
 * A macro call defined in a build file
 */
public class MacroCall {

    private final BazelBuildFile buildFile;
    private final CallExpression callExpression;
    private final String localFunctionName;
    private final Map<String, Argument> argumentsByArgumentName;
    private final String originalFunctionName;

    /**
     * @param buildFile
     *            the owning build file
     * @param callExpression
     *            the call expression
     * @param buildFileBindings
     *            map of bindings in the build file to lookup an original macro name by its local name
     */
    public MacroCall(BazelBuildFile buildFile, CallExpression callExpression, Map<String, String> buildFileBindings) {
        this.buildFile = buildFile;
        this.callExpression = callExpression;

        if (!(callExpression.getFunction() instanceof Identifier)) {
            throw new IllegalArgumentException(
                    format("Unsupported call statement - must use an identifer as function (%s)",
                        callExpression.getStartLocation()));
        }

        localFunctionName = requireNonNull(((Identifier) callExpression.getFunction()).getName(),
            "null identifier is unexpected at this point");

        originalFunctionName = buildFileBindings.get(localFunctionName);

        // this might fail if there are multiple attributes of the same name
        // TODO: confirm with Bazel what the behavior/expectation should be
        argumentsByArgumentName =
                callExpression.getArguments().stream().collect(toMap(Argument::getName, Function.identity()));
    }

    public BazelBuildFile getBazelBuildFile() {
        return buildFile;
    }

    public CallExpression getCallExpression() {
        return callExpression;
    }

    /**
     * Collects and returns all <code>glob</code> information of a a named argument.
     *
     * @param name
     *            name of the argument
     * @return the glob information of a named argument (maybe <code>null</code> if the argument is not present)
     */
    public List<GlobInfo> getGlobInfoFromArgument(String name) {
        var argument = argumentsByArgumentName.get(name);

        if (argument == null) {
            return null;
        }

        // heuristically extract glob patterns
        var globs = new ArrayList<GlobInfo>();
        argument.getValue().accept(new NodeVisitor() {

            // adapted from https://github.com/bazelbuild/bazel/blob/c2e25ea70376b5c5dc7e304eb986163c332621d5/src/main/java/com/google/devtools/build/lib/packages/PackageFactory.java#LL516C1-L566C12
            void extractGlobPatterns(CallExpression call) {
                if (call.getFunction() instanceof Identifier) {
                    List<String> includePatterns = new ArrayList<>();
                    List<String> excludePatterns = new ArrayList<>();
                    var functionName = ((Identifier) call.getFunction()).getName();
                    if (!functionName.equals("glob")) {
                        return;
                    }

                    Expression include = null;
                    Expression exclude = null;
                    var arguments = call.getArguments();
                    for (var i = 0; i < arguments.size(); i++) {
                        var arg = arguments.get(i);
                        var name = arg.getName();
                        if (name == null) {
                            if (i == 0) { // first positional argument
                                include = arg.getValue();
                            }
                        } else if (name.equals("include")) {
                            include = arg.getValue();
                        } else if (name.equals("exclude")) {
                            exclude = arg.getValue();
                        }
                    }
                    if (include instanceof ListExpression) {
                        for (Expression elem : ((ListExpression) include).getElements()) {
                            if (elem instanceof StringLiteral) {
                                var pattern = ((StringLiteral) elem).getValue();
                                includePatterns.add(pattern);
                            }
                        }
                    }
                    if (exclude instanceof ListExpression) {
                        for (Expression elem : ((ListExpression) exclude).getElements()) {
                            if (elem instanceof StringLiteral) {
                                var pattern = ((StringLiteral) elem).getValue();
                                excludePatterns.add(pattern);
                            }
                        }
                    }
                    if (!includePatterns.isEmpty()) {
                        globs.add(new GlobInfo(includePatterns, excludePatterns));
                    }
                }
            }

            @Override
            public void visit(CallExpression node) {
                extractGlobPatterns(node);

                // continue to find nested calls
                super.visit(node);
            }
        });

        return globs;
    }

    /**
     * {@return the value of a name argument (maybe <code>null</code> if not present)}
     */
    public String getName() {
        return getStringArgument("name");
    }

    /**
     * Returns the resolved function name called by the macro.
     * <p>
     * The returned name is resolved using bindings available when loading a build file. Thus, the resolved function
     * name is always the original name from any imported <code>bzl</code> files.
     * </p>
     *
     * @return name of the function being called
     */
    public String getResolvedFunctionName() {
        return originalFunctionName != null ? originalFunctionName : localFunctionName;
    }

    /**
     * Returns the value as String of a named argument.
     * <p>
     * Returns <code>null</code> if the argument is not present. If the argument present but not a
     * {@link StringLiteral}, <code>null</code> will be returned.
     * </p>
     *
     * @param name
     *            name of the argument
     * @return the value of a named argument as string (maybe <code>null</code> if the argument is not present)
     */
    public String getStringArgument(String name) {
        var argument = argumentsByArgumentName.get(name);

        if ((argument != null) && argument.getValue() instanceof StringLiteral stringLiteral) {
            return stringLiteral.getValue();
        }

        return null; // treat as not present
    }

    /**
     * Returns the value of a named argument as list of string.
     * <p>
     * Returns <code>null</code> if the argument is not present. If the argument present but not a
     * {@link ListExpression},, <code>null</code> will be returned.
     * </p>
     *
     * @param name
     *            name of the argument
     * @return the value of a named argument as string (maybe <code>null</code> if the argument is not present)
     */
    public List<String> getStringListArgument(String name) {
        var argument = argumentsByArgumentName.get(name);

        if ((argument != null) && argument.getValue() instanceof ListExpression listExpression) {
            return listExpression.getElements()
                    .stream()
                    .filter(StringLiteral.class::isInstance)
                    .map(StringLiteral.class::cast)
                    .map(StringLiteral::getValue)
                    .collect(toList());
        }

        return null; // treat as not present
    }

    @Override
    public String toString() {
        var name = getName();
        if (name == null) {
            name = "<unnamed>";
        }

        if (originalFunctionName != null) {
            return format("%s(%s, kind %s) in %s", localFunctionName, name, originalFunctionName,
                callExpression.getStartLocation());
        }
        return format("%s(%s) in %s", localFunctionName, name, callExpression.getStartLocation());
    }

}
