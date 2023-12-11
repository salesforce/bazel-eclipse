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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import com.salesforce.bazel.eclipse.core.model.buildfile.FunctionCall;

import net.starlark.java.syntax.CallExpression;
import net.starlark.java.syntax.LoadStatement;

/**
 * Element info for {@link BazelBuildFile}
 *
 * @param <O>
 *            element info owner
 */
public sealed abstract class BazelFileInfo<O extends BazelFile<?, ?>> extends BazelElementInfo
        permits BazelBuildFileInfo, BazelModuleFileInfo {

    private final O bazelFile;
    private final List<LoadStatement> loadStatements;
    private final List<FunctionCall> functionCalls;
    private final Map<String, String> callBindingsByLocalName;

    BazelFileInfo(O bazelFile, List<LoadStatement> loadStatements, List<CallExpression> functionCalls) {
        this.bazelFile = bazelFile;
        // note: the load statements become relevant at some point to map from private to public name
        this.loadStatements = loadStatements;
        callBindingsByLocalName = loadStatements.stream()
                .flatMap(l -> l.getBindings().stream())
                .collect(toMap(b -> b.getLocalName().getName(), b -> b.getOriginalName().getName()));
        this.functionCalls =
                functionCalls.stream().map(e -> new FunctionCall(bazelFile, e, callBindingsByLocalName)).collect(toList());
    }

    public O getBazelFile() {
        return bazelFile;
    }

    List<LoadStatement> getLoadStatements() {
        return loadStatements;
    }

    public List<FunctionCall> getFunctionCalls() {
        return functionCalls;
    }

    /**
     * Returns a list of all macro calls calling a specific function.
     * <p>
     * Note, the macros will be searched based on their {@link FunctionCall#getResolvedFunctionName() resolved function
     * name}. Thus, any local name in the build file is resolved to the original public name.
     * </p>
     *
     * @param functionName
     *            the original name
     * @return a list of macro calls
     */
    public List<FunctionCall> getMacroCallsForFunction(String functionName) {
        var function = callBindingsByLocalName.containsKey(functionName) ? callBindingsByLocalName.get(functionName)
                : functionName;
        return functionCalls.stream().filter(m -> function.equals(m.getResolvedFunctionName())).collect(toList());
    }

    @Override
    public BazelFile<?, ?> getOwner() {
        return bazelFile;
    }
}
