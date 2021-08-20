package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class TestJavaFileCreator {
    /**
     * Write a Java source file that is legal Java but not very interesting.
     *
     */
    static void createJavaSourceFile(File javaFile, String javaPackage, String classname) {
        StringBuffer javaContent = new StringBuffer();

        javaContent.append("// Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.\n");
        javaContent.append("// This is a generated test java file. See TestJavaFileCreator in the Bazel Java SDK.\n");
        // throw the parser a curve ball, make sure it doesn't mistake this for the package
        javaContent.append("// package not.the.real.package;\n");

        // write the real package, throw in some extra whitespace to be tricky
        javaContent.append("package     ");
        javaContent.append(javaPackage);
        javaContent.append(";     \n\n");

        // toss in some imports
        javaContent.append("import java.io.File;\n");
        javaContent.append("import java.io.FileOutputStream;\n");
        javaContent.append("import java.io.PrintStream;\n\n");

        javaContent.append("public class ");
        javaContent.append(classname);
        javaContent.append(" {\n");
        javaContent.append("    public String testStr;\n");
        javaContent.append("}\n");

        try (PrintStream out = new PrintStream(new FileOutputStream(javaFile))) {
            out.print(javaContent.toString());
        } catch (Exception anyE) {
            anyE.printStackTrace();
        }
    }

}
