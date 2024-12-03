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

import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/**
 * Exposes {@link GlobInfo} to Starlark
 */
@StarlarkBuiltin(name = "glob_info", documented = false)
class StarlarkGlobInfo implements StarlarkValue {

    private final GlobInfo globInfo;

    public StarlarkGlobInfo(GlobInfo globInfo) {
        this.globInfo = globInfo;
    }

    /**
     * {@return {@link GlobInfo#exclude()}}
     */
    @StarlarkMethod(name = "exclude", structField = true)
    public Sequence<String> exclude() {
        return StarlarkList.immutableCopyOf(globInfo.exclude());
    }

    /**
     * {@return {@link GlobInfo#include()}}
     */
    @StarlarkMethod(name = "info", structField = true)
    public Sequence<String> include() {
        return StarlarkList.immutableCopyOf(globInfo.include());
    }
}
