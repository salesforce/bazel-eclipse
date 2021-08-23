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
package com.salesforce.bazel.sdk.lang.jvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelSourceFile;

/**
 * Models a java source file.
 */
public class JavaSourceFile extends BazelSourceFile {
    static final LogHelper LOG = LogHelper.log(JavaSourceFile.class);

    public JavaSourceFile(File javaFile) {
        if (javaFile == null) {
            throw new IllegalArgumentException("Caller passed a null JavaFile to the constructor.");
        }
        absolutePath = javaFile.getAbsolutePath();
        this.sourceFile = javaFile;

        if (!absolutePath.endsWith(".java")) {
            throw new IllegalArgumentException("Caller passed a non JavaFile to the constructor. Path: "+absolutePath);
        }
    }

    /**
     * Returns the JVM package (ex: "a.b.c") if the "package a.b.c;" line is in this File, null otherwise.
     */
    public String readPackageFromFile() {
        if (!sourceFile.exists()) {
            throw new IllegalStateException("Cannot parse missing JavaFile: " + sourceFile.getAbsolutePath());
        }
        String packageName = null;

        try (Reader reader = new FileReader(sourceFile)) {
            packageName = getPackageFromReader(reader);
        } catch (Exception anyE) {
            LOG.error(anyE.getMessage(), anyE);
        }

        return packageName;
    }

    /**
     * Returns the JVM package if the "package a.b.c;" line is in this reader, null otherwise.
     */
    String getPackageFromReader(Reader lines) {
        try (BufferedReader br = new BufferedReader(lines)) {
            String javaFileLine = br.readLine();
            while (javaFileLine != null) {
                String packageName = getPackageFromLine(javaFileLine);
                if (packageName != null) {
                    return packageName;
                }
                javaFileLine = br.readLine();
            }

        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Returns the JVM package if it is on this line, null otherwise.
     */
    String getPackageFromLine(String line) {
        line = line.trim();
        String packageName = null;
        if (line.startsWith("package")) {
            int lineLength = line.length();

            if (lineLength < 10) {
                // somebody put the package statement and packageName on different lines, cant be bothered to support this
                return null;
            }

            if (line.endsWith(";")) {
                packageName = line.substring(8, lineLength - 1);
            } else {
                LOG.warn("This package line [{}] from Java file [{}] does not end in a semicolon", line,
                    sourceFile.getAbsolutePath());
                packageName = line.substring(8);
            }

            // remove any extra whitespace caused by crazies like this:
            //   package      com.salesforce.foo       ;
            packageName = packageName.trim();
        }
        return packageName;
    }

}
