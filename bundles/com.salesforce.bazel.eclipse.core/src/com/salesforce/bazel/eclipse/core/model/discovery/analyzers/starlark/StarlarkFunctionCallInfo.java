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

import java.util.Map;

import org.eclipse.core.runtime.OperationCanceledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.salesforce.bazel.eclipse.core.model.buildfile.FunctionCall;

import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.Dict.Builder;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.syntax.Argument;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.ParserInput;

/**
 * A data type which exposes {@link FunctionCall} info to Starlark .
 */
@StarlarkBuiltin(name = "function_call_info", documented = false)
class StarlarkFunctionCallInfo implements StarlarkValue {

    private static final Map<String, Object> predeclared;
    static {
        ImmutableMap.Builder<String, Object> predeclaredEnvBuilder = ImmutableMap.builder();
        Starlark.addMethods(predeclaredEnvBuilder, new StarlarkNativeModuleApiDummy());
        predeclared = predeclaredEnvBuilder.build();
    }

    private static final Logger LOG = LoggerFactory.getLogger(StarlarkFunctionCallInfo.class);

    private final FunctionCall functionCall;
    private volatile Dict<String, Object> args;

    public StarlarkFunctionCallInfo(FunctionCall functionCall) {
        this.functionCall = functionCall;
    }

    private Dict<String, Object> evaluateArgs(StarlarkThread thread) {
        Builder<String, Object> result = Dict.builder();

        var callExpression = functionCall.getCallExpression();
        for (Argument argument : callExpression.getArguments()) {
            if (argument.getName() == null) {
                continue;
            }
            try {
                var parserInput = ParserInput
                        .fromString(argument.getValue().prettyPrint(), format("argument %s", argument.getName()));
                result.put(argument.getName(), Starlark.eval(parserInput, FileOptions.DEFAULT, predeclared, thread));
            } catch (InterruptedException e) {
                throw new OperationCanceledException("Interrupted Starlark execution");
            } catch (Exception e) {
                LOG.warn("Unable to evaluate argument '{}' in function call '{}'", argument.getName(), functionCall, e);
            }

        }

        return result.buildImmutable();
    }

    @StarlarkMethod(name = "args", structField = true, useStarlarkThread = true)
    public Dict<String, Object> getArgs(StarlarkThread thread) {
        var args = this.args;
        if (args != null) {
            return args;
        }
        return this.args = evaluateArgs(thread);
    }

    @StarlarkMethod(name = "resolved_function_name", structField = true)
    public String getResolvedFunctionName() {
        return functionCall.getResolvedFunctionName();
    }
}
