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

import com.salesforce.bazel.eclipse.core.model.buildfile.MacroCall;

import net.starlark.java.syntax.CallExpression;
import net.starlark.java.syntax.LoadStatement;

/**
 * Element info for {@link BazelBuildFile}
 */
public final class BazelBuildFileInfo extends BazelElementInfo {

    private final BazelBuildFile bazelBuildFile;
    private final List<LoadStatement> loadStatements;
    private final List<MacroCall> macroCalls;
    private final Map<String, String> macroCallBindingsByLocalName;

    BazelBuildFileInfo(BazelBuildFile bazelBuildFile, List<LoadStatement> loadStatements,
            List<CallExpression> macroCalls) {
        this.bazelBuildFile = bazelBuildFile;
        // note: the load statements become relevant at some point to map from private to public bame
        this.loadStatements = loadStatements;
        macroCallBindingsByLocalName = loadStatements.stream()
                .flatMap(l -> l.getBindings().stream())
                .collect(toMap(b -> b.getLocalName().getName(), b -> b.getOriginalName().getName()));
        this.macroCalls = macroCalls.stream()
                .map(e -> new MacroCall(bazelBuildFile, e, macroCallBindingsByLocalName))
                .collect(toList());
    }

    public BazelBuildFile getBazelBuildFile() {
        return bazelBuildFile;
    }

    List<LoadStatement> getLoadStatements() {
        return loadStatements;
    }

    public List<MacroCall> getMacroCalls() {
        return macroCalls;
    }

    /**
     * Returns a list of all macro calls calling a specific function.
     * <p>
     * Note, the macros will be searched based on their {@link MacroCall#getResolvedFunctionName() resolved function
     * name}. Thus, any local name in the build file is resolved to the original public name.
     * </p>
     *
     * @param functionName
     *            the original name
     * @return a list of macro calls
     */
    public List<MacroCall> getMacroCallsForFunction(String functionName) {
        var function = macroCallBindingsByLocalName.containsKey(functionName)
                ? macroCallBindingsByLocalName.get(functionName) : functionName;
        return macroCalls.stream().filter(m -> function.equals(m.getResolvedFunctionName())).collect(toList());
    }
}
