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

import com.salesforce.bazel.eclipse.core.model.buildfile.GlobInfo;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkInt;

/**
 * A dummy of the Bazel Build API <code>native</code> module to allow intercepting <code>glob</code> in evaluations.
 */
@StarlarkBuiltin(name = "native", documented = false)
public class StarlarkNativeModuleApiDummy {

    /**
     * Support for <code>glob</code> to turn into {@link StarlarkGlobInfo}.
     *
     * @see https://github.com/bazelbuild/bazel/blob/984d1bad444797db0d60692c9dfaadc6d450752e/src/main/java/com/google/devtools/build/lib/starlarkbuildapi/StarlarkNativeModuleApi.java#L91
     */
    @StarlarkMethod(name = "glob", documented = false, parameters = {
            @Param(name = "include", allowedTypes = {
                    @ParamType(type = Sequence.class, generic1 = String.class) }, defaultValue = "[]", named = true, documented = false),
            @Param(name = "exclude", allowedTypes = {
                    @ParamType(type = Sequence.class, generic1 = String.class) }, defaultValue = "[]", named = true, documented = false),
            @Param(name = "exclude_directories", defaultValue = "1", named = true, documented = false),
            @Param(name = "allow_empty", defaultValue = "unbound", named = true, documented = false) })
    StarlarkGlobInfo glob(Sequence<String> include, Sequence<String> exclude, StarlarkInt excludeDirectories,
            Object allowEmpty) throws EvalException, InterruptedException {
        return new StarlarkGlobInfo(new GlobInfo(include, exclude));
    }
}
