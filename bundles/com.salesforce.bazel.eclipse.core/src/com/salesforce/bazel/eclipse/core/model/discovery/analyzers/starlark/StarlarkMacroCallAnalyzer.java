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
package com.salesforce.bazel.eclipse.core.model.discovery.analyzers.starlark;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.buildfile.FunctionCall;
import com.salesforce.bazel.eclipse.core.model.discovery.MacroCallAnalyzer;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.SyntaxError;

/**
 * A generic analyzer for {@link FunctionCall} which delegates analysis to a Starlark function.
 * <p>
 * This allows contributing {@link MacroCallAnalyzer} in Starlark outside the IDE. The Starlark execution is limited,
 * though. Bazel specific Starlark globals are not supported.
 * </p>
 * <p>
 * The analyzer must be defined in a <code>.bzl</code> file. The file must define a function named <code>analyze</code>.
 * The function will be called with the following named parameters:
 * <ul>
 * <li><code>function_info</code> - info about the function call (see {@link StarlarkFunctionCallInfo} for details)</li>
 * </ul>
 * </p>
 * <p>
 * The following limitations apply:
 * <ul>
 * <li>Function arguments will only be detected if they evaluate to a simple value, i.e. complex expressions may not
 * yield a value and thus need to be skipped.</li>
 * </ul>
 * </p>
 */
public class StarlarkMacroCallAnalyzer implements MacroCallAnalyzer {

    private static final String FUNCTION_INFO = "function_info";

    private static final StarlarkSemantics starlarkSemantics =
            StarlarkSemantics.builder().setBool(StarlarkSemantics.EXPERIMENTAL_ENABLE_STARLARK_SET, true).build();

    /**
     * Parses the given input for an <code>'analyze'</code> function.
     *
     * @param input
     *            the input to parse
     * @param file
     *            for error reporting only
     * @return the parsed function (never <code>null</code>)
     * @throws CoreException,
     *             {@link OperationCanceledException}
     */
    /* for test only */
    static StarlarkFunction parseInputAndGetAnalyzeFunction(ParserInput input, String file)
            throws CoreException, OperationCanceledException {
        try (var mu = Mutability.create("analyzer")) {
            ImmutableMap.Builder<String, Object> env = ImmutableMap.builder();
            //Starlark.addMethods(env, new CqueryDialectGlobals(), starlarkSemantics);
            var module = Module.withPredeclared(starlarkSemantics, env.buildOrThrow());

            var thread = StarlarkThread.createTransient(mu, starlarkSemantics);
            Starlark.execFile(input, FileOptions.DEFAULT, module, thread);
            var analyzeFn = module.getGlobal("analyze");
            if (analyzeFn == null) {
                throw new CoreException(Status.error(format("File '%s' does not define 'analyze' function", file)));
            }
            if (!(analyzeFn instanceof StarlarkFunction analyzeFunction)) {
                throw new CoreException(
                        Status.error(
                            format(
                                "File '%s' 'analyze' is not a function. Got '%s'.",
                                file,
                                Starlark.type(analyzeFn))));
            }
            if (!analyzeFunction.getParameterNames().contains(FUNCTION_INFO)
                    || (analyzeFunction.getParameterNames().size() != 1)) {
                throw new CoreException(
                        Status.error(
                            format(
                                "File '%s' 'analyze' function must take exactly 1 named argument 'function_info'",
                                file)));
            }

            return analyzeFunction;
        } catch (SyntaxError.Exception e) {
            throw new CoreException(Status.error(format("Syntax error in file '%s': %s", file, e.getMessage()), e));
        } catch (EvalException e) {
            throw new CoreException(Status.error(format("Evaluation error in file '%s': %s", file, e.getMessage()), e));
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted while executing Starlark");
        }
    }

    private final IPath analyzeFile;

    private final StarlarkFunction analyzeFunction;

    public StarlarkMacroCallAnalyzer(BazelWorkspace bazelWorkspace, WorkspacePath bzlFile)
            throws CoreException, OperationCanceledException {

        analyzeFile = bazelWorkspace.getLocation().append(bzlFile.relativePath());

        ParserInput input;
        try {
            input = ParserInput.readFile(analyzeFile.toOSString());
        } catch (IOException e) {
            throw new CoreException(Status.error(format("Failed to read file '%s'", analyzeFile), e));
        }

        analyzeFunction = parseInputAndGetAnalyzeFunction(input, analyzeFile.toOSString());
    }

    @Override
    public boolean analyze(FunctionCall macroCall, JavaProjectInfo javaInfo) throws CoreException {
        try {
            var thread = StarlarkThread.createTransient(Mutability.create("analyze evaluation"), starlarkSemantics);
            thread.setMaxExecutionSteps(500_000L);

            var kwargs = Map.<String, Object> of(FUNCTION_INFO, new StarlarkFunctionCallInfo(macroCall));
            var result = Starlark.call(thread, analyzeFunction, null, kwargs);
            if (Starlark.isNullOrNone(result) || !Starlark.truth(result)) {
                return false;
            }
            if (!(result instanceof StarlarkAnalyzeInfo)) {
                throw Starlark.errorf("Return value is not of type AnalyzeInfo. Got '%s'", result);
            }
        } catch (EvalException e) {
            throw new CoreException(
                    Status.error(
                        format("Error executiong 'analyze' in file '%s': %s", analyzeFile, e.getMessage()),
                        e));
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted Starlark execution");
        }

        return false;
    }

}
