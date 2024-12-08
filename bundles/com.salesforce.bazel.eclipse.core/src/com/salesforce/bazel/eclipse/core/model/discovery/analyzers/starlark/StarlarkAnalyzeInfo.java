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

import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.eclipse.core.model.buildfile.GlobInfo;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkValue;

/**
 * A data type for returning information from the <code>analyze</code> function
 */
@StarlarkBuiltin(name = "AnalyzeInfo", documented = false)
public class StarlarkAnalyzeInfo implements StarlarkValue {

    private List<String> convertToStringList(Sequence<?> exclude, String nameForErrorMessage) throws EvalException {
        List<String> stringList = new ArrayList<>();
        for (Object value : exclude) {
            if (!(value instanceof String s)) {
                throw Starlark.errorf("Invalid 'glob' argument type in '%s': %s", nameForErrorMessage, value);
            }
            stringList.add(s);
        }
        return stringList;
    }

    /**
     * Creates the project information
     */
    @StarlarkMethod(name = "ProjectInfo", documented = false, parameters = {
            @Param(name = "srcDirectories", allowedTypes = {
                    @ParamType(type = Sequence.class, generic1 = String.class) }, defaultValue = "[]", named = true, documented = false),
            @Param(name = "exclude", allowedTypes = {
                    @ParamType(type = Sequence.class, generic1 = String.class) }, defaultValue = "[]", named = true, documented = false),
            @Param(name = "exclude_directories", defaultValue = "1", named = true, documented = false),
            @Param(name = "allow_empty", defaultValue = "unbound", named = true, documented = false) })
    StarlarkGlobInfo ProjectInfo(Sequence<?> include, Sequence<?> exclude, StarlarkInt excludeDirectories,
            Object allowEmpty) throws EvalException, InterruptedException {

        var includeStringList = convertToStringList(include, "include");
        var excludeStringList = convertToStringList(exclude, "exclude");
        return new StarlarkGlobInfo(new GlobInfo(includeStringList, excludeStringList));
    }
}
