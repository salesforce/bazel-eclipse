/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.sdk.workspace.test.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * Utility class that writes a .java file on disk. It has enough text in it to be a legal Java file (package
 * declaration, public class statement, etc)
 */
public class TestJavaFileCreator {
    /**
     * Write a Java source file that is legal Java code with a proper package name.
     */
    public static void createJavaSourceFile(File javaFile, String javaPackage, String classname) {
        StringBuffer javaContent = new StringBuffer();

        addCopyrightHeader(javaContent);
        addPackage(javaContent, javaPackage);
        addImports(javaContent);
        addClass(javaContent, classname);
        writeFile(javaContent, javaFile);
    }

    /**
     * Write a Java source file that is in the default package (no package statement).
     */
    public static void createJavaSourceFileInDefaultPackage(File javaFile, String classname) {
        StringBuffer javaContent = new StringBuffer();

        addCopyrightHeader(javaContent);
        addImports(javaContent);
        addClass(javaContent, classname);
        writeFile(javaContent, javaFile);
    }

    // INTERNAL HELPERS

    private static void addCopyrightHeader(StringBuffer javaContent) {
        javaContent.append("// Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.\n");
        javaContent.append("// This is a generated test java file. See TestJavaFileCreator in the Bazel Java SDK.\n\n");
        javaContent.append(
                "// Throw the parser a curve ball, make sure it doesn't mistake this comment for the package\n");
        javaContent.append("// package not.the.real.package;\n");
    }

    private static void addPackage(StringBuffer javaContent, String javaPackage) {
        // write the real package, throw in some extra whitespace to be tricky
        javaContent.append("package     ");
        javaContent.append(javaPackage);
        javaContent.append(";     \n\n");
    }

    private static void addImports(StringBuffer javaContent) {
        javaContent.append("import java.io.File;\n");
        javaContent.append("import java.io.FileOutputStream;\n");
        javaContent.append("import java.io.PrintStream;\n\n");
    }

    private static void addClass(StringBuffer javaContent, String classname) {
        javaContent.append("public class ");
        javaContent.append(classname);
        javaContent.append(" {\n");
        javaContent.append("    public String testStr;\n");
        javaContent.append("}\n");
    }

    private static void writeFile(StringBuffer javaContent, File javaFile) {
        try (PrintStream out = new PrintStream(new FileOutputStream(javaFile))) {
            out.print(javaContent.toString());
        } catch (Exception anyE) {
            anyE.printStackTrace();
        }
    }
}
