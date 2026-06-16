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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.core.runtime.CoreException;
import org.junit.jupiter.api.Test;

import net.starlark.java.syntax.ParserInput;

class StarlarkMacroCallAnalyzerTest {

    @Test
    void parseInputAndGetAnalyzeFunction_fails_for_missing_function() throws Exception {
        assertThrows(CoreException.class, () -> {
            StarlarkMacroCallAnalyzer.parseInputAndGetAnalyzeFunction(ParserInput.fromString("""
                    def analyze_wrong_name():
                        return None
                    """, "test only"), "test only");
        });
        assertThrows(CoreException.class, () -> {
            StarlarkMacroCallAnalyzer.parseInputAndGetAnalyzeFunction(ParserInput.fromString("""
                    """, "test only"), "test only");
        });
    }

    @Test
    void parseInputAndGetAnalyzeFunction_fails_for_missing_or_misnamed_args() throws Exception {
        assertThrows(CoreException.class, () -> {
            StarlarkMacroCallAnalyzer.parseInputAndGetAnalyzeFunction(ParserInput.fromString("""
                    def analyze():
                        return None
                    """, "test only"), "test only");
        });
        assertThrows(CoreException.class, () -> {
            StarlarkMacroCallAnalyzer.parseInputAndGetAnalyzeFunction(ParserInput.fromString("""
                    def analyze(wrong_name):
                        return None
                    """, "test only"), "test only");
        });
        assertThrows(CoreException.class, () -> {
            StarlarkMacroCallAnalyzer.parseInputAndGetAnalyzeFunction(ParserInput.fromString("""
                    def analyze(function_info, additional_name):
                        return None
                    """, "test only"), "test only");
        });
    }

    @Test
    void parseInputAndGetAnalyzeFunction_ok() throws Exception {
        StarlarkMacroCallAnalyzer.parseInputAndGetAnalyzeFunction(ParserInput.fromString("""
                def analyze(function_info):
                    return None
                """, "test only"), "test only");
    }
}
