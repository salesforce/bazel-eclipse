/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.index.jvm;

import java.io.File;

import com.salesforce.bazel.sdk.index.CodeIndex;
import com.salesforce.bazel.sdk.index.model.ClassIdentifier;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.index.source.SourceFileCrawler;
import com.salesforce.bazel.sdk.index.source.SourceFileIdentifier;

/**
 * Crawler that descends into nested directories of Java source files and adds found files to the index.
 */
public class JavaSourceCrawler extends SourceFileCrawler {

    public JavaSourceCrawler(CodeIndex index, String artifactMarkerFileName) {
        super(index, artifactMarkerFileName);

        // this crawler is simple, it just looks for .java files
        this.matchFileSuffixes.add(".java");
    }

    /**
     * Callback that is invoked when a Java source file is found. We add the source file the type index.
     */
    protected void foundSourceFile(File sourceFile, CodeLocationDescriptor sourceLocationDescriptor) {
        // isolate the classname by stripping off the .java and the prefix
        String fqClassName = sourceFile.getPath();
        fqClassName = fqClassName.substring(0, fqClassName.length() - 5);
        int javaIndex = fqClassName.indexOf("java"); // TODO this only works for projects that follow Maven conventions
        fqClassName = fqClassName.substring(javaIndex + 5);
        fqClassName = fqClassName.replace(File.separator, ".");

        ClassIdentifier classId = new ClassIdentifier(fqClassName);
        SourceFileIdentifier sourceFileId = new SourceFileIdentifier(sourceLocationDescriptor, classId);
        CodeLocationDescriptor sourceFileLocationDescriptor = new CodeLocationDescriptor(sourceFile, sourceFileId);

        sourceFileLocationDescriptor.addClass(classId);
        index.addTypeLocation(fqClassName, sourceFileLocationDescriptor);
    }

}
