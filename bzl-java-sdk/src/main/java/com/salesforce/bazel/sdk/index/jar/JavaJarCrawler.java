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
package com.salesforce.bazel.sdk.index.jar;

import java.io.File;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.salesforce.bazel.sdk.index.index.CodeIndex;
import com.salesforce.bazel.sdk.index.model.ClassIdentifier;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;

public class JavaJarCrawler {

    private CodeIndex index;
    private JarIdentiferResolver resolver;

    public JavaJarCrawler(CodeIndex index, JarIdentiferResolver resolver) {
        this.index = index;
        this.resolver = resolver;
    }

    public void index(File basePath, boolean doIndexClasses) {
        indexRecur(basePath, doIndexClasses);
    }

    protected void indexRecur(File path, boolean doIndexClasses) {
        File[] children = path.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            ZipFile zipFile = null;
            try {
                if (child.isDirectory()) {
                    if (child.getPath().contains(".runfiles")) {
                        // bazel test sandbox, stay out of here as the jars in here are for running tests
                        return;
                    }
                    indexRecur(child, doIndexClasses);
                } else if (child.canRead()) {
                    if (child.getName().endsWith(".jar")) {
                        zipFile = new ZipFile(child);
                        foundJar(child, zipFile, doIndexClasses);
                    }
                }
            }
            catch (Exception anyE) {
                log("Reading jar file lead to unexpected error", anyE.getMessage(), child.getPath());
                anyE.printStackTrace();
            } finally {
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (Exception ioE) {}
                }
            }
        }
    }

    protected void foundJar(File jarFile, ZipFile zipFile, boolean doIndexClasses) {
        // precisely identify the jar file
        JarIdentifier jarId = resolver.resolveJarIdentifier(jarFile, zipFile);
        if (jarId == null) {
            // this jar is not part of the typical dependencies (e.g. it is a jar used in the build toolchain); ignore
            return;
        }
        CodeLocationDescriptor jarLocationDescriptor = new CodeLocationDescriptor(jarFile, jarId);

        // add to our index using artifact name
        index.addArtifactLocation(jarId.artifact, jarLocationDescriptor);

        // if we don't want an index of each class found in a jar, bail here and save a lot of work
        if (!doIndexClasses) {
            return;
        }
        
        // add to our index using classnames
        Enumeration<? extends ZipEntry> entries = null;
        try {
            entries = zipFile.entries();
        } catch (Exception anyE) {
            log("failure opening zipfile", jarFile.getPath(), null);
            return;
        }
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String fqClassname = entry.getName();
            if (fqClassname == null) {
                // whatever
                continue;
            } else if (!fqClassname.endsWith(".class")) {
                // non-class file, don't care
                continue;
            } else if (fqClassname.endsWith("package-info.class")) {
                // non-class file, don't care
                continue;
            } else if (fqClassname.contains("$")) {
                // inner class, don't care
                continue;
            }
            // convert path / into . to form the legal package name, and trim the .class off the end
            fqClassname = fqClassname.replace("/", ".");
            fqClassname = fqClassname.substring(0, fqClassname.length()-6);
            //System.out.println("Classname: "+fqClassname+" from jar "+jarId.locationIdentifier);

            ClassIdentifier classId = new ClassIdentifier(fqClassname);
            jarLocationDescriptor.addClass(classId);
            index.addClassnameLocation(classId.classname, jarLocationDescriptor);
        }
    }

    protected static void log(String msg, String param1, String param2) {
        if (param1 == null) {
            System.err.println(msg);
        } else if (param2 == null) {
            System.err.println(msg+" ["+param1+"] ");
        } else {
            System.err.println(msg+" ["+param1+"] ["+param2+"]");
        }
    }

}
