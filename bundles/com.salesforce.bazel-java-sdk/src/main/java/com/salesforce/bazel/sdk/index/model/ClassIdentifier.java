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
package com.salesforce.bazel.sdk.index.model;

import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Holder object for a type: package name + class name
 */
public class ClassIdentifier {
    private static final LogHelper LOG = LogHelper.log(ClassIdentifier.class);

    public String packageName;
    public String classname;

    public ClassIdentifier(String packageName, String classname) {
        this.packageName = packageName;
        this.classname = classname;
    }

    public ClassIdentifier(String fqClassname) {
        int lastDot = fqClassname.lastIndexOf(".");
        if (lastDot == -1) {
            // brave soul, they used the default package
            packageName = "";
            classname = fqClassname;
        } else {
            packageName = fqClassname.substring(0, lastDot);
            classname = fqClassname.substring(lastDot + 1);
        }
    }

    @Override
    public String toString() {
        return packageName + "." + classname;
    }

    private void printId() {
        LOG.info("p[{}] c[{}]", packageName, classname);
    }

    public static void main(String[] args) {
        new ClassIdentifier("com.salesforce.blue.Dog").printId();
        new ClassIdentifier("Dog").printId();
    }
}
