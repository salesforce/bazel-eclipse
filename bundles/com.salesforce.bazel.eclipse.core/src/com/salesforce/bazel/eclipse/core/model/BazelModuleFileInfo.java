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

import java.util.List;

import net.starlark.java.syntax.CallExpression;
import net.starlark.java.syntax.LoadStatement;

/**
 * Element info for {@link BazelModuleFile}
 */
public final class BazelModuleFileInfo extends BazelFileInfo<BazelModuleFile> {

    private final CallExpression moduleCall;

    BazelModuleFileInfo(BazelModuleFile bazelModuleFile, List<LoadStatement> loadStatements,
            List<CallExpression> macroCalls, CallExpression moduleCall) {
        super(bazelModuleFile, loadStatements, macroCalls);
        this.moduleCall = moduleCall;
    }

    public CallExpression getModuleCall() {
        return moduleCall;
    }

}
